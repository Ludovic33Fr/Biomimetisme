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
  activeIOCs: new Map<string, IOC & { startTime: number; endTime: number }>()
};

const votes = new Map<string, Set<string>>(); // key=kind|value -> sources
function keyOf(ioc: IOC){ return `${ioc.kind}|${ioc.value}`; }

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
    lastSeen: node.lastSeen || Date.now()
  }));
  
  broadcast({
    type: "state", 
    payload: { 
      nodes: nodesWithMetrics,
      activeIOCs: Array.from(state.activeIOCs.values()),
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

      if (votes.get(k)!.size >= QUORUM){
        const shared = { ...ioc, ttl_sec: DEFAULT_TTL };
        const now = Date.now();
        const activeIOC = {
          ...shared,
          startTime: now,
          endTime: now + (shared.ttl_sec * 1000)
        };
        state.activeIOCs.set(k, activeIOC);
        
        nc.publish("ioc.share", sc.encode(JSON.stringify(shared)));
        broadcast({type:"event", payload:{...shared, eventType:"ioc.share"}});
      }
    }
  });
})().catch((e)=>{
  console.error("[controller] fatal error", e);
  process.exit(1);
});
