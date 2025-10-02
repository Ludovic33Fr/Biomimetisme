// controller/src/index.ts
import { connect, StringCodec } from "nats";
import WebSocket, { WebSocketServer } from "ws";
import * as http from "http";
import * as fs from "fs";
import * as path from "path";

const NATS_URL = process.env.NATS_URL || "nats://bus:4222";
const QUORUM = parseInt(process.env.QUORUM || "1", 10);
const DEFAULT_TTL = parseInt(process.env.DEFAULT_TTL_SEC || "180", 10);
const WS_PORT = parseInt(process.env.WS_PORT || "8080", 10);
const HTTP_PORT = parseInt(process.env.HTTP_PORT || "3000", 10);

const sc = StringCodec();

type IOC = {
  kind: "ip" | "ua" | "path";
  value: string;
  reason: string;
  source: string;
  confidence: number;
  ttl_sec: number;
  firstSeen: number;
};

type NodeState = { 
  id: string; 
  alerts: number; 
  drops: number; 
  health: "ok"|"under-attack";
  lastSeen: number;
  alerts_1m: number;
  drops_1m: number;
};

const state = {
  nodes: new Map<string, NodeState>(),
  events: [] as any[],
  activeIOCs: new Map<string, IOC & { startTime: number; endTime: number }>(),
  metrics: {
    mttd: 0, // Mean Time To Detect
    mttr: 0, // Mean Time To Respond
    containmentTime: 0,
    containmentRatio: 0,
    firstOffensiveEvent: new Map<string, number>(), // nodeId -> timestamp
    firstAlert: new Map<string, number>(), // nodeId -> timestamp
    firstIOCShare: new Map<string, number>(), // iocKey -> timestamp
    lastDropByIOC: new Map<string, number>() // iocKey -> timestamp
  },
  nodeReputation: new Map<string, number>(), // nodeId -> score [0,1]
  globalQuorum: QUORUM
};

const votes = new Map<string, Set<string>>(); // key=kind|value -> sources
function keyOf(ioc: IOC){ return `${ioc.kind}|${ioc.value}`; }

// Fonctions pour calculer les métriques avancées
function calculateMTTD() {
  let totalTime = 0;
  let count = 0;
  
  for (const [nodeId, firstOffensive] of state.metrics.firstOffensiveEvent) {
    const firstAlert = state.metrics.firstAlert.get(nodeId);
    if (firstAlert && firstAlert > firstOffensive) {
      totalTime += (firstAlert - firstOffensive);
      count++;
    }
  }
  
  return count > 0 ? totalTime / count : 0;
}

function calculateMTTR() {
  let totalTime = 0;
  let count = 0;
  
  for (const [nodeId, firstAlert] of state.metrics.firstAlert) {
    const firstIOCShare = state.metrics.firstIOCShare.get(nodeId);
    if (firstIOCShare && firstIOCShare > firstAlert) {
      totalTime += (firstIOCShare - firstAlert);
      count++;
    }
  }
  
  return count > 0 ? totalTime / count : 0;
}

function calculateContainmentTime() {
  let totalTime = 0;
  let count = 0;
  
  for (const [iocKey, firstShare] of state.metrics.firstIOCShare) {
    const lastDrop = state.metrics.lastDropByIOC.get(iocKey);
    if (lastDrop && lastDrop > firstShare) {
      totalTime += (lastDrop - firstShare);
      count++;
    }
  }
  
  return count > 0 ? totalTime / count : 0;
}

function calculateContainmentRatio() {
  const totalNodes = state.nodes.size;
  if (totalNodes === 0) return 0;
  
  let nodesNeverAlertedAfterIOC = 0;
  
  for (const [nodeId, node] of state.nodes) {
    const firstAlert = state.metrics.firstAlert.get(nodeId);
    if (!firstAlert) continue; // Skip nodes that never had an alert
    
    let hasIOCAfterAlert = false;
    
    for (const [iocKey, firstShare] of state.metrics.firstIOCShare) {
      if (firstShare > firstAlert) {
        hasIOCAfterAlert = true;
        break;
      }
    }
    
    if (!hasIOCAfterAlert) {
      nodesNeverAlertedAfterIOC++;
    }
  }
  
  return (nodesNeverAlertedAfterIOC / totalNodes) * 100;
}

function updateNodeReputation(nodeId: string, isFalsePositive: boolean) {
  const currentRep = state.nodeReputation.get(nodeId) || 0.5;
  const adjustment = isFalsePositive ? -0.1 : 0.05;
  const newRep = Math.max(0, Math.min(1, currentRep + adjustment));
  state.nodeReputation.set(nodeId, newRep);
}

function calculateWeightedQuorum(ioc: IOC): number {
  let weightedVotes = 0;
  const sources = votes.get(keyOf(ioc)) || new Set();
  
  for (const source of sources) {
    const reputation = state.nodeReputation.get(source) || 0.5;
    weightedVotes += reputation;
  }
  
  return weightedVotes;
}

// Serveur HTTP pour servir l'interface web
const httpServer = http.createServer((req, res) => {
  if (req.url === '/' || req.url === '/index.html') {
    const filePath = path.join(__dirname, '../public/index.html');
    fs.readFile(filePath, (err, data) => {
      if (err) {
        res.writeHead(404);
        res.end('File not found');
        return;
      }
      res.writeHead(200, { 'Content-Type': 'text/html' });
      res.end(data);
    });
  } else if (req.url === '/visual' || req.url === '/visual.html') {
    const filePath = path.join(__dirname, '../public/visual.html');
    fs.readFile(filePath, (err, data) => {
      if (err) {
        res.writeHead(404);
        res.end('File not found');
        return;
      }
      res.writeHead(200, { 'Content-Type': 'text/html' });
      res.end(data);
    });
  } else {
    res.writeHead(404);
    res.end('Not found');
  }
});

const wss = new WebSocketServer({ server: httpServer });

function broadcast(obj:any){
  const msg = JSON.stringify(obj);
  for (const client of wss.clients) if (client.readyState === WebSocket.OPEN) client.send(msg);
}

// Handle WebSocket connections and commands
wss.on('connection', (ws) => {
  console.log('Client WebSocket connecté');
  
  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message.toString());
      
      if (data.type === 'command') {
        switch (data.action) {
          case 'updateQuorum':
            state.globalQuorum = data.value;
            console.log(`Quorum global mis à jour: ${data.value}`);
            break;
          case 'expireIOC':
            const iocKey = `${data.kind}|${data.value}`;
            state.activeIOCs.delete(iocKey);
            console.log(`IOC expiré: ${iocKey}`);
            break;
          case 'extendIOC':
            const extendKey = `${data.kind}|${data.value}`;
            const ioc = state.activeIOCs.get(extendKey);
            if (ioc) {
              ioc.endTime += 60000; // +60 seconds
              state.activeIOCs.set(extendKey, ioc);
              console.log(`IOC étendu: ${extendKey}`);
            }
            break;
          case 'quarantineIOC':
            const quarantineKey = `${data.kind}|${data.value}`;
            const quarantineIOC = state.activeIOCs.get(quarantineKey);
            if (quarantineIOC) {
              quarantineIOC.endTime = Date.now() + (24 * 60 * 60 * 1000); // 24 hours
              state.activeIOCs.set(quarantineKey, quarantineIOC);
              console.log(`IOC mis en quarantaine: ${quarantineKey}`);
            }
            break;
        }
      }
    } catch (e) {
      console.error('Erreur parsing commande WebSocket:', e);
    }
  });
  
  ws.on('close', () => {
    console.log('Client WebSocket déconnecté');
  });
});

// Nettoyage périodique des IOCs expirés et reset des métriques 1m
setInterval(() => {
  const now = Date.now();
  
  // Nettoyer les IOCs expirés
  for (const [key, ioc] of state.activeIOCs) {
    if (now >= ioc.endTime) {
      state.activeIOCs.delete(key);
    }
  }
  
  // Reset des métriques 1m toutes les minutes
  if (now % 60000 < 1000) {
    for (const [id, node] of state.nodes) {
      node.alerts_1m = 0;
      node.drops_1m = 0;
    }
  }
}, 1000);

// Broadcast périodique de l'état
setInterval(() => {
  const nodesWithMetrics = Array.from(state.nodes.values()).map(node => ({
    ...node,
    alerts_1m: node.alerts_1m || 0,
    drops_1m: node.drops_1m || 0,
    lastSeen: node.lastSeen || Date.now(),
    reputation: state.nodeReputation.get(node.id) || 0.5
  }));
  
  // Calculer les métriques en temps réel
  state.metrics.mttd = calculateMTTD();
  state.metrics.mttr = calculateMTTR();
  state.metrics.containmentTime = calculateContainmentTime();
  state.metrics.containmentRatio = calculateContainmentRatio();
  
  broadcast({
    type: "state", 
    payload: { 
      nodes: nodesWithMetrics,
      activeIOCs: Array.from(state.activeIOCs.values()),
      metrics: {
        mttd: Math.round(state.metrics.mttd),
        mttr: Math.round(state.metrics.mttr),
        containmentTime: Math.round(state.metrics.containmentTime / 1000), // en secondes
        containmentRatio: Math.round(state.metrics.containmentRatio)
      },
      globalQuorum: state.globalQuorum,
      timestamp: Date.now()
    }
  });
}, 500);

(async()=>{
  const nc = await connect({ servers: NATS_URL });
  console.log(`[controller] connected to ${NATS_URL}, HTTP on ${HTTP_PORT}, WS on /ws`);
  
  // Démarrer le serveur HTTP
  httpServer.listen(HTTP_PORT, () => {
    console.log(`[controller] HTTP server running on port ${HTTP_PORT}`);
  });

  // Découverte des nodes
  await nc.subscribe("nodes.hello", {
    callback: (_e,m) => {
      try {
        const hello = JSON.parse(sc.decode(m.data));
        const id = hello.nodeId || "unknown";
        const now = Date.now();
        const n = state.nodes.get(id) || { 
          id, 
          alerts: 0, 
          drops: 0, 
          health: "ok" as const,
          lastSeen: now,
          alerts_1m: 0,
          drops_1m: 0
        };
        n.lastSeen = now;
        state.nodes.set(id, n);
        broadcast({type:"event", payload:{kind:"hello", nodeId:id, ts: hello.ts}});
      } catch (err) { /* ignore */ }
    }
  });

  // Alerts
  await nc.subscribe("alerts.*", {
    callback: (_e,m)=>{
      const alert = JSON.parse(sc.decode(m.data));
      const id = alert.nodeId;
      const now = Date.now();
      const n = state.nodes.get(id) || {
        id, 
        alerts:0, 
        drops:0, 
        health:"ok",
        lastSeen: now,
        alerts_1m: 0,
        drops_1m: 0
      };
      n.alerts++; 
      n.alerts_1m++;
      n.health = "under-attack";
      n.lastSeen = now;
      state.nodes.set(id, n);
      
      // Track first alert for MTTD calculation
      if (!state.metrics.firstAlert.has(id)) {
        state.metrics.firstAlert.set(id, now);
      }
      
      // Track offensive events (simplified - in reality you'd detect actual attacks)
      if (!state.metrics.firstOffensiveEvent.has(id)) {
        state.metrics.firstOffensiveEvent.set(id, now - Math.random() * 5000); // Simulate attack start
      }
      
      broadcast({type:"event", payload:{kind:"alert", ...alert}});
    }
  });

  // Drops
  await nc.subscribe("drops.*", {
    callback: (_e,m)=>{
      const drop = JSON.parse(sc.decode(m.data));
      const id = drop.nodeId;
      const now = Date.now();
      const n = state.nodes.get(id) || {
        id, 
        alerts:0, 
        drops:0, 
        health:"ok",
        lastSeen: now,
        alerts_1m: 0,
        drops_1m: 0
      };
      n.drops++; 
      n.drops_1m++;
      n.lastSeen = now;
      state.nodes.set(id, n);
      
      // Track drops for containment time calculation
      if (drop.iocKey) {
        state.metrics.lastDropByIOC.set(drop.iocKey, now);
      }
      
      broadcast({type:"event", payload:{kind:"drop", ...drop}});
    }
  });

  // IOC quorum -> share
  await nc.subscribe("ioc.local", {
    callback: (_e, m)=>{
      const ioc = JSON.parse(sc.decode(m.data)) as IOC;
      const k = keyOf(ioc);
      if (!votes.has(k)) votes.set(k, new Set());
      votes.get(k)!.add(ioc.source);
      broadcast({type:"event", payload:{...ioc, eventType:"ioc.local"}});

      // Use weighted quorum based on reputation
      const weightedVotes = calculateWeightedQuorum(ioc);
      const requiredQuorum = state.globalQuorum;
      
      if (weightedVotes >= requiredQuorum || votes.get(k)!.size >= QUORUM){
        const shared = { ...ioc, ttl_sec: DEFAULT_TTL };
        const now = Date.now();
        const activeIOC = {
          ...shared,
          startTime: now,
          endTime: now + (shared.ttl_sec * 1000)
        };
        state.activeIOCs.set(k, activeIOC);
        
        // Track first IOC share for MTTR calculation
        if (!state.metrics.firstIOCShare.has(k)) {
          state.metrics.firstIOCShare.set(k, now);
        }
        
        nc.publish("ioc.share", sc.encode(JSON.stringify(shared)));
        broadcast({type:"event", payload:{...shared, eventType:"ioc.share"}});
      }
    }
  });
})().catch((e)=>{
  console.error("[controller] fatal error", e);
  process.exit(1);
});
