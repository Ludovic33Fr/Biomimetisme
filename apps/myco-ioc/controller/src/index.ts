// controller/src/index.ts
import { connect, StringCodec } from "nats";
import WebSocket, { WebSocketServer } from "ws";
import * as http from "http";
import * as fs from "fs";
import * as path from "path";
import { log } from "./log";

const NATS_URL = process.env.NATS_URL || "nats://bus:4222";
const QUORUM = parseInt(process.env.QUORUM || "1", 10);
const DEFAULT_TTL = parseInt(process.env.DEFAULT_TTL_SEC || "180", 10);
const WS_PORT = parseInt(process.env.WS_PORT || "8080", 10);
const HTTP_PORT = parseInt(process.env.HTTP_PORT || "3000", 10);

// Version du système - lue depuis package.json (source unique)
const pkg = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "package.json"), "utf-8"));
const SYSTEM_VERSION = pkg.version;
const BUILD_TIMESTAMP = new Date().toISOString();

// SLO/SLA Configuration
const SLO_MTTD_MAX = 2000; // 2s en ms
const SLO_MTTR_MAX = 3000; // 3s en ms  
const SLO_CONTAINMENT_MAX = 10000; // 10s en ms
const NODE_ISOLATION_TIMEOUT = 15000; // 15s en ms
const MAX_BLOCKLIST_ENTRIES = parseInt(process.env.MAX_BLOCKLIST_ENTRIES || "100", 10);
const IOC_FLOOD_THRESHOLD = parseInt(process.env.IOC_FLOOD_THRESHOLD || "10", 10); // IOC/s
const FAIL_MODE = process.env.FAIL_MODE || "fail-open"; // fail-open ou fail-closed

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
  health: "ok"|"protected"|"isolated";
  lastSeen: number;
  alerts_1m: number;
  drops_1m: number;
  lastHeartbeat: number;
  blocklistEntries: number;
  iocAcks: Map<string, number>; // iocKey -> timestamp
  reputation: number;
};

type SLOAlert = {
  type: "reactivity_degraded" | "containment_breach" | "ioc_flood" | "node_isolated" | "sync_ko" | "blocklist_saturation";
  nodeId?: string;
  iocKey?: string;
  timestamp: number;
  severity: "warning" | "critical";
  message: string;
  metrics?: {
    mttd?: number;
    mttr?: number;
    containment?: number;
    iocRate?: number;
  };
};

type SLOMetrics = {
  mttd: number;
  mttr: number;
  containmentTime: number;
  containmentRatio: number;
  iocRate: number; // IOC/s
  coverage: number;
  sloViolations: {
    mttd: number;
    mttr: number;
    containment: number;
  };
  consecutiveViolations: {
    mttd: number;
    mttr: number;
    containment: number;
  };
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
  globalQuorum: QUORUM,
  
  // SLO/SLA State
  sloAlerts: [] as SLOAlert[],
  sloMetrics: {
    mttd: 0,
    mttr: 0,
    containmentTime: 0,
    containmentRatio: 0,
    iocRate: 0,
    coverage: 0,
    sloViolations: { mttd: 0, mttr: 0, containment: 0 },
    consecutiveViolations: { mttd: 0, mttr: 0, containment: 0 }
  } as SLOMetrics,
  
  // IOC Flood Detection
  iocLocalCount: 0,
  iocLocalWindow: [] as number[], // timestamps des derniers ioc.local
  floodMode: false,
  
  // Containment Breach Detection
  containmentBreaches: new Map<string, number>(), // iocKey -> breach count
  
  // System Health
  controllerHealth: "healthy" as "healthy" | "degraded" | "critical",
  failMode: FAIL_MODE as "fail-open" | "fail-closed"
};

const votes = new Map<string, Set<string>>(); // key=kind|value -> sources
function keyOf(ioc: IOC){ return `${ioc.kind}|${ioc.value}`; }

// SLO/SLA Functions
function addSLOAlert(alert: SLOAlert) {
  state.sloAlerts.unshift(alert);
  if (state.sloAlerts.length > 100) {
    state.sloAlerts.pop(); // Keep only last 100 alerts
  }
  
  // Broadcast alert to WebSocket clients
  broadcast({
    type: "slo_alert",
    payload: alert
  });
  
  const logFn = alert.severity === "critical" ? log.error : log.warn;
  logFn("SLO alert", { type: alert.type, severity: alert.severity, message: alert.message, node_id: alert.nodeId, ioc_key: alert.iocKey });
}

function checkSLOViolations() {
  const now = Date.now();
  const metrics = state.sloMetrics;
  
  // Check MTTD violation
  if (metrics.mttd > SLO_MTTD_MAX) {
    metrics.sloViolations.mttd++;
    metrics.consecutiveViolations.mttd++;
    
    if (metrics.consecutiveViolations.mttd >= 3) {
      addSLOAlert({
        type: "reactivity_degraded",
        timestamp: now,
        severity: "critical",
        message: `MTTD dégradé: ${Math.round(metrics.mttd)}ms (SLO: ${SLO_MTTD_MAX}ms)`,
        metrics: { mttd: metrics.mttd }
      });
      metrics.consecutiveViolations.mttd = 0; // Reset counter
    }
  } else {
    metrics.consecutiveViolations.mttd = 0;
  }
  
  // Check MTTR violation
  if (metrics.mttr > SLO_MTTR_MAX) {
    metrics.sloViolations.mttr++;
    metrics.consecutiveViolations.mttr++;
    
    if (metrics.consecutiveViolations.mttr >= 3) {
      addSLOAlert({
        type: "reactivity_degraded",
        timestamp: now,
        severity: "critical",
        message: `MTTR dégradé: ${Math.round(metrics.mttr)}ms (SLO: ${SLO_MTTR_MAX}ms)`,
        metrics: { mttr: metrics.mttr }
      });
      metrics.consecutiveViolations.mttr = 0;
    }
  } else {
    metrics.consecutiveViolations.mttr = 0;
  }
  
  // Check Containment violation
  if (metrics.containmentTime > SLO_CONTAINMENT_MAX) {
    metrics.sloViolations.containment++;
    metrics.consecutiveViolations.containment++;
    
    if (metrics.consecutiveViolations.containment >= 3) {
      addSLOAlert({
        type: "reactivity_degraded",
        timestamp: now,
        severity: "critical",
        message: `Containment dégradé: ${Math.round(metrics.containmentTime)}ms (SLO: ${SLO_CONTAINMENT_MAX}ms)`,
        metrics: { containment: metrics.containmentTime }
      });
      metrics.consecutiveViolations.containment = 0;
    }
  } else {
    metrics.consecutiveViolations.containment = 0;
  }
}

function checkNodeIsolation() {
  const now = Date.now();
  
  for (const [nodeId, node] of state.nodes) {
    const timeSinceHeartbeat = now - node.lastHeartbeat;
    
    if (timeSinceHeartbeat > NODE_ISOLATION_TIMEOUT) {
      if (node.health !== "isolated") {
        node.health = "isolated";
        addSLOAlert({
          type: "node_isolated",
          nodeId: nodeId,
          timestamp: now,
          severity: "critical",
          message: `Nœud ${nodeId} isolé depuis ${Math.round(timeSinceHeartbeat/1000)}s`
        });
      }
    } else if (node.health === "isolated") {
      node.health = "ok"; // Node came back online
    }
  }
}

function checkIOCFlood() {
  const now = Date.now();
  const windowStart = now - 1000; // 1 second window
  
  // Clean old timestamps
  state.iocLocalWindow = state.iocLocalWindow.filter(ts => ts > windowStart);
  
  const currentRate = state.iocLocalWindow.length;
  state.sloMetrics.iocRate = currentRate;
  
  if (currentRate > IOC_FLOOD_THRESHOLD && !state.floodMode) {
    state.floodMode = true;
    state.globalQuorum = Math.min(state.globalQuorum + 1, 10); // Increase quorum
    
    addSLOAlert({
      type: "ioc_flood",
      timestamp: now,
      severity: "warning",
      message: `IOC flood détecté: ${currentRate} IOC/s (seuil: ${IOC_FLOOD_THRESHOLD})`,
      metrics: { iocRate: currentRate }
    });
    
    log.warn("IOC flood mode activated", { quorum: state.globalQuorum });
  } else if (currentRate <= IOC_FLOOD_THRESHOLD / 2 && state.floodMode) {
    state.floodMode = false;
    state.globalQuorum = Math.max(state.globalQuorum - 1, QUORUM); // Restore normal quorum
    
    addSLOAlert({
      type: "ioc_flood",
      timestamp: now,
      severity: "warning",
      message: `IOC flood résolu - Retour au quorum normal: ${state.globalQuorum}`,
      metrics: { iocRate: currentRate }
    });
  }
}

function checkContainmentBreach(iocKey: string) {
  const breachCount = state.containmentBreaches.get(iocKey) || 0;
  const newBreachCount = breachCount + 1;
  state.containmentBreaches.set(iocKey, newBreachCount);
  
  if (newBreachCount >= state.globalQuorum) {
    addSLOAlert({
      type: "containment_breach",
      iocKey: iocKey,
      timestamp: Date.now(),
      severity: "critical",
      message: `IOC ${iocKey} inefficace - ${newBreachCount} nœuds déclenchent encore des alertes`
    });
    
    // Escalate: increase quorum and apply stricter policy
    state.globalQuorum = Math.min(state.globalQuorum + 1, 10);
    log.warn("containment breach - escalated quorum", { quorum: state.globalQuorum });
  }
}

function checkBlocklistSaturation(nodeId: string) {
  const node = state.nodes.get(nodeId);
  if (node && node.blocklistEntries >= MAX_BLOCKLIST_ENTRIES) {
    addSLOAlert({
      type: "blocklist_saturation",
      nodeId: nodeId,
      timestamp: Date.now(),
      severity: "warning",
      message: `Nœud ${nodeId} - Blocklist saturée (${node.blocklistEntries}/${MAX_BLOCKLIST_ENTRIES})`
    });
    
    // Apply LRU: expire oldest entries
    // This would be implemented in the node itself, but we log it here
    log.warn("blocklist saturation - LRU recommended", { node_id: nodeId });
  }
}

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

function updateSLOMetrics() {
  // Update SLO metrics with current calculated values
  state.sloMetrics.mttd = state.metrics.mttd;
  state.sloMetrics.mttr = state.metrics.mttr;
  state.sloMetrics.containmentTime = state.metrics.containmentTime;
  state.sloMetrics.containmentRatio = state.metrics.containmentRatio;
  
  // Calculate coverage (nodes with active IOCs / total nodes)
  const nodesWithIOC = Array.from(state.nodes.values()).filter(node => node.alerts > 0).length;
  state.sloMetrics.coverage = state.nodes.size > 0 ? (nodesWithIOC / state.nodes.size) * 100 : 0;
  
  // Check for SLO violations
  checkSLOViolations();
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
  } else if (req.url === '/test-timeline') {
    const filePath = path.join(__dirname, '../public/test-timeline.html');
    fs.readFile(filePath, (err, data) => {
      if (err) {
        res.writeHead(404);
        res.end('File not found');
        return;
      }
      res.writeHead(200, { 'Content-Type': 'text/html' });
      res.end(data);
    });
  } else if (req.url === '/api/state') {
    // Snapshot JSON pour les tests smoke et l'observabilité externe.
    const snapshot = {
      version: SYSTEM_VERSION,
      nodes: Array.from(state.nodes.values()).map(n => ({
        id: n.id, health: n.health, alerts: n.alerts, drops: n.drops,
        lastSeen: n.lastSeen
      })),
      activeIOCs: Array.from(state.activeIOCs.values()).map(ioc => ({
        kind: ioc.kind, value: ioc.value, source: ioc.source,
        confidence: ioc.confidence, ttl_sec: ioc.ttl_sec,
        startTime: ioc.startTime, endTime: ioc.endTime
      })),
      globalQuorum: state.globalQuorum,
      floodMode: state.floodMode,
      timestamp: Date.now()
    };
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(snapshot));
  } else {
    res.writeHead(404);
    res.end('Not found');
  }
});

const wss = new WebSocketServer({ server: httpServer });

function handleTrafficControl(command: string, targetNodeId?: string) {
  log.info("traffic control command", { command, target_node_id: targetNodeId });
  
  switch (command) {
    case 'stop':
      // Arrêter le trafic en publiant un message de contrôle
      if (natsConnection) {
        natsConnection.publish("traffic.control", sc.encode(JSON.stringify({
          action: 'stop',
          timestamp: Date.now()
        })));
        log.info("traffic stopped");
      }
      break;
      
    case 'low':
      // Trafic faible (1 requête toutes les 2 secondes)
      if (natsConnection) {
        natsConnection.publish("traffic.control", sc.encode(JSON.stringify({
          action: 'low',
          interval: 2000,
          timestamp: Date.now()
        })));
        log.info("traffic mode: low");
      }
      break;
      
    case 'normal':
      // Trafic normal (1 requête toutes les 60ms)
      if (natsConnection) {
        natsConnection.publish("traffic.control", sc.encode(JSON.stringify({
          action: 'normal',
          interval: 60,
          timestamp: Date.now()
        })));
        log.info("traffic mode: normal");
      }
      break;
      
    case 'attack':
      // Déclencher une attaque immédiate
      if (natsConnection) {
        const knownNodes = Array.from(state.nodes.keys());
        const targetNode = (targetNodeId && state.nodes.has(targetNodeId))
          ? targetNodeId
          : knownNodes[Math.floor(Math.random() * knownNodes.length)];
        if (!targetNode) {
          log.warn("no attack target available");
          break;
        }
        const badIP = '203.0.113.66';
        
        // Envoyer 30 requêtes d'attaque
        for (let i = 0; i < 30; i++) {
          setTimeout(() => {
            const attackEvent = {
              nodeId: targetNode,
              ts: Date.now(),
              src_ip: badIP,
              path: '/wp-login.php',
              status: 401,
              ua: 'B4dB0t'
            };
            natsConnection.publish("traffic.http", sc.encode(JSON.stringify(attackEvent)));
          }, i * 50); // 50ms entre chaque requête
        }
        log.info("attack triggered", { target_node_id: targetNode });
      }
      break;
      
    default:
      log.warn("unknown traffic control command", { command });
  }
}

function broadcast(obj:any){
  const msg = JSON.stringify(obj);
  for (const client of wss.clients) if (client.readyState === WebSocket.OPEN) client.send(msg);
}

// Handle WebSocket connections and commands
let natsConnection: any = null; // Store NATS connection globally

wss.on('connection', (ws) => {
  log.info("websocket client connected");
  
  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message.toString());
      
      if (data.type === 'command') {
        switch (data.action) {
          case 'updateQuorum':
            state.globalQuorum = data.value;
            log.info("global quorum updated", { quorum: data.value });
            break;
          case 'expireIOC':
            const iocKey = `${data.kind}|${data.value}`;
            state.activeIOCs.delete(iocKey);
            log.info("IOC expired", { ioc_key: iocKey });
            break;
          case 'extendIOC':
            const extendKey = `${data.kind}|${data.value}`;
            const ioc = state.activeIOCs.get(extendKey);
            if (ioc) {
              ioc.endTime += 60000; // +60 seconds
              state.activeIOCs.set(extendKey, ioc);
              log.info("IOC extended", { ioc_key: extendKey });
            }
            break;
          case 'quarantineIOC':
            const quarantineKey = `${data.kind}|${data.value}`;
            const quarantineIOC = state.activeIOCs.get(quarantineKey);
            if (quarantineIOC) {
              quarantineIOC.endTime = Date.now() + (24 * 60 * 60 * 1000); // 24 hours
              state.activeIOCs.set(quarantineKey, quarantineIOC);
              log.info("IOC quarantined", { ioc_key: quarantineKey });
            }
            break;
          case 'simulateFalsePositive':
            // Simulate a false positive for demo purposes
            const fpNodeId = data.nodeId || 'node-1';
            const fpIOC = {
              kind: 'ip' as const,
              value: '192.168.1.100',
              reason: 'Simulation FP',
              source: fpNodeId,
              confidence: 95,
              ttl_sec: 60,
              firstSeen: Date.now()
            };
            if (natsConnection) {
              natsConnection.publish("ioc.local", sc.encode(JSON.stringify(fpIOC)));
              log.info("FP simulation triggered", { node_id: fpNodeId });
            } else {
              log.warn("NATS connection not available for simulation");
            }
            break;
          case 'simulateIOCFlood':
            // Simulate multiple IOC.local events for flood detection
            const floodNodeId = 'node-flood';
            for (let i = 0; i < 15; i++) {
              const floodIOC = {
                kind: 'ip' as const,
                value: `192.168.1.${100 + i}`,
                reason: 'Simulation Flood',
                source: floodNodeId,
                confidence: 80,
                ttl_sec: 30,
                firstSeen: Date.now()
              };
              if (natsConnection) {
                natsConnection.publish("ioc.local", sc.encode(JSON.stringify(floodIOC)));
              }
            }
            log.info("IOC flood simulation triggered");
            break;
          case 'simulateNodeIsolation':
            // Simulate a node going offline
            const isolatedNodeId = 'node-isolated';
            const isolatedNode = state.nodes.get(isolatedNodeId) || {
              id: isolatedNodeId,
              alerts: 0,
              drops: 0,
              health: "ok" as const,
              lastSeen: Date.now(),
              alerts_1m: 0,
              drops_1m: 0,
              lastHeartbeat: Date.now() - 20000, // 20s ago
              blocklistEntries: 0,
              iocAcks: new Map(),
              reputation: 0.5
            };
            isolatedNode.health = "isolated";
            state.nodes.set(isolatedNodeId, isolatedNode);
            broadcast({type:"event", payload:{kind:"node_isolated", nodeId: isolatedNodeId, reason: "Simulation isolation"}});
            log.info("node isolation simulation triggered", { node_id: isolatedNodeId });
            break;
          case 'toggleFailMode':
            state.failMode = state.failMode === 'fail-open' ? 'fail-closed' : 'fail-open';
            log.info("fail-mode changed", { fail_mode: state.failMode });
            break;
          case 'trafficControl':
            handleTrafficControl(data.command, data.targetNodeId);
            break;
        }
      }
    } catch (e) {
      log.error("error parsing WS command", { err: e });
    }
  });
  
  ws.on('close', () => {
    log.info("websocket client disconnected");
  });
});

// Nettoyage périodique des IOCs expirés et vérifications SLO
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
  
  // Vérifications SLO/SLA toutes les 5 secondes
  if (now % 5000 < 1000) {
    checkNodeIsolation();
    checkIOCFlood();
    updateSLOMetrics();
  }
}, 1000);

// Broadcast périodique de l'état
setInterval(() => {
  const nodesWithMetrics = Array.from(state.nodes.values()).map(node => ({
    ...node,
    alerts_1m: node.alerts_1m || 0,
    drops_1m: node.drops_1m || 0,
    lastSeen: node.lastSeen || Date.now(),
    lastHeartbeat: node.lastHeartbeat || Date.now(),
    blocklistEntries: node.blocklistEntries || 0,
    reputation: state.nodeReputation.get(node.id) || 0.5
  }));
  
  // Calculer les métriques en temps réel
  state.metrics.mttd = calculateMTTD();
  state.metrics.mttr = calculateMTTR();
  state.metrics.containmentTime = calculateContainmentTime();
  state.metrics.containmentRatio = calculateContainmentRatio();
  
  // Update SLO metrics
  updateSLOMetrics();
  
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
      sloMetrics: {
        mttd: Math.round(state.sloMetrics.mttd),
        mttr: Math.round(state.sloMetrics.mttr),
        containmentTime: Math.round(state.sloMetrics.containmentTime / 1000),
        containmentRatio: Math.round(state.sloMetrics.containmentRatio),
        iocRate: Math.round(state.sloMetrics.iocRate * 10) / 10,
        coverage: Math.round(state.sloMetrics.coverage),
        sloViolations: state.sloMetrics.sloViolations,
        consecutiveViolations: state.sloMetrics.consecutiveViolations
      },
      sloAlerts: state.sloAlerts.slice(0, 10), // Last 10 alerts
      globalQuorum: state.globalQuorum,
      floodMode: state.floodMode,
      controllerHealth: state.controllerHealth,
      failMode: state.failMode,
      systemVersion: SYSTEM_VERSION,
      buildTimestamp: BUILD_TIMESTAMP,
      timestamp: Date.now()
    }
  });
}, 500);

(async()=>{
  const nc = await connect({ servers: NATS_URL });
  natsConnection = nc; // Store connection globally for WebSocket commands
  log.info("connected to NATS", { url: NATS_URL, http_port: HTTP_PORT, version: SYSTEM_VERSION });
  
  // Démarrer le serveur HTTP
  httpServer.listen(HTTP_PORT, () => {
    log.info("HTTP server listening", { port: HTTP_PORT });
  });

  // Synchronisation des IOC actifs pour les nouveaux nœuds
  await nc.subscribe("ioc.sync.request", {
    callback: async (_e, m) => {
      try {
        const request = JSON.parse(sc.decode(m.data));
        const nodeId = request.nodeId || "unknown";
        const now = Date.now();
        
        // Préparer la liste des IOC actifs (non expirés)
        const activeIOCsList = Array.from(state.activeIOCs.entries())
          .filter(([_, ioc]) => now < ioc.endTime)
          .map(([key, ioc]) => {
            // Calculer le TTL restant en secondes
            const remainingTtlSec = Math.max(0, Math.floor((ioc.endTime - now) / 1000));
            return {
              kind: ioc.kind,
              value: ioc.value,
              reason: ioc.reason,
              source: ioc.source,
              confidence: ioc.confidence,
              ttl_sec: remainingTtlSec,
              firstSeen: ioc.firstSeen
            };
          });
        
        const response = {
          nodeId: nodeId,
          iocs: activeIOCsList,
          timestamp: now
        };
        
        // Répondre avec les IOC actifs
        if (m.reply) {
          nc.publish(m.reply, sc.encode(JSON.stringify(response)));
          log.info("sent active IOCs for sync", { node_id: nodeId, count: activeIOCsList.length });
        }
      } catch (err) {
        log.error("error handling IOC sync request", { err });
      }
    }
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
          drops_1m: 0,
          lastHeartbeat: now,
          blocklistEntries: 0,
          iocAcks: new Map(),
          reputation: 0.5
        };
        n.lastSeen = now;
        // Treat nodes.hello as a heartbeat as well to avoid stale heartbeat-based isolation
        n.lastHeartbeat = now;
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
        drops_1m: 0,
        lastHeartbeat: now,
        blocklistEntries: 0,
        iocAcks: new Map(),
        reputation: 0.5
      };
      n.alerts++; 
      n.alerts_1m++;
      // Ne pas isoler le nœud qui détecte l'attaque - il reste opérationnel
      // n.health reste "ok" car le nœud a déjà appliqué le blocage local
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
      
      // Check for containment breach if this alert is for an active IOC
      if (alert.iocKey) {
        checkContainmentBreach(alert.iocKey);
      }
      
      // Check blocklist saturation
      checkBlocklistSaturation(id);
      
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
        drops_1m: 0,
        lastHeartbeat: now,
        blocklistEntries: 0,
        iocAcks: new Map(),
        reputation: 0.5
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
      const now = Date.now();
      
      // Track IOC flood detection
      state.iocLocalWindow.push(now);
      
      if (!votes.has(k)) votes.set(k, new Set());
      votes.get(k)!.add(ioc.source);
        broadcast({type:"event", payload:{kind:"ioc.local", iocKind: ioc.kind, value: ioc.value, reason: ioc.reason, source: ioc.source, confidence: ioc.confidence, ttl_sec: ioc.ttl_sec, firstSeen: ioc.firstSeen}});

      // Use weighted quorum based on reputation
      const weightedVotes = calculateWeightedQuorum(ioc);
      const requiredQuorum = state.globalQuorum;
      
      if (weightedVotes >= requiredQuorum || votes.get(k)!.size >= QUORUM){
        const shared = { ...ioc, ttl_sec: DEFAULT_TTL };
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
        broadcast({type:"event", payload:{kind:"ioc.share", iocKind: shared.kind, value: shared.value, reason: shared.reason, source: shared.source, confidence: shared.confidence, ttl_sec: shared.ttl_sec, firstSeen: shared.firstSeen}});
      }
    }
  });

  // IOC Acknowledgments
  await nc.subscribe("ack.*", {
    callback: (_e, m) => {
      try {
        const ack = JSON.parse(sc.decode(m.data));
        const nodeId = ack.nodeId;
        const iocKey = ack.iocKey;
        const now = Date.now();
        
        const node = state.nodes.get(nodeId);
        if (node) {
          node.iocAcks.set(iocKey, now);
          
          // Marquer le nœud comme "protected" s'il applique des IOCs
          if (node.health === "ok") {
            node.health = "protected";
          }
          
          // Check for sync issues - if node doesn't ack IOC shares
          const ioc = state.activeIOCs.get(iocKey);
          if (ioc && (now - ioc.startTime) > 10000) { // 10s timeout
            addSLOAlert({
              type: "sync_ko",
              nodeId: nodeId,
              iocKey: iocKey,
              timestamp: now,
              severity: "warning",
              message: `Nœud ${nodeId} n'applique pas l'IOC ${iocKey}`
            });
          }
        }
        
        broadcast({type:"event", payload:{kind:"ack", nodeId, iocKey, ts: ack.ts}});
      } catch (err) { /* ignore */ }
    }
  });

  // Controller metrics publishing
  setInterval(() => {
    const metrics = {
      mttd: state.sloMetrics.mttd,
      mttr: state.sloMetrics.mttr,
      coverage: state.sloMetrics.coverage,
      containmentRatio: state.sloMetrics.containmentRatio,
      ioc_rate: state.sloMetrics.iocRate,
      timestamp: Date.now()
    };
    
    nc.publish("metrics.controller", sc.encode(JSON.stringify(metrics)));
  }, 10000); // Every 10 seconds

})().catch((e)=>{
  log.error("fatal error", { err: e });
  process.exit(1);
});
