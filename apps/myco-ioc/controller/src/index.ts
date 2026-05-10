// controller/src/index.ts
import { connect, StringCodec } from "nats";
import WebSocket, { WebSocketServer } from "ws";
import * as http from "http";
import * as fs from "fs";
import * as path from "path";
import { log } from "./log";
import {
  AppState,
  IOC,
  FailMode,
  createState,
  defaultNode,
  keyOf,
  calculateWeightedQuorum,
} from "./state";
import { createSLO, SLO } from "./slo";
import { attachWSCommands } from "./commands";

const NATS_URL = process.env.NATS_URL || "nats://bus:4222";
const QUORUM = parseInt(process.env.QUORUM || "1", 10);
const DEFAULT_TTL = parseInt(process.env.DEFAULT_TTL_SEC || "180", 10);
const HTTP_PORT = parseInt(process.env.HTTP_PORT || "3000", 10);
const MAX_BLOCKLIST_ENTRIES = parseInt(process.env.MAX_BLOCKLIST_ENTRIES || "100", 10);
const IOC_FLOOD_THRESHOLD = parseInt(process.env.IOC_FLOOD_THRESHOLD || "10", 10);
const FAIL_MODE = (process.env.FAIL_MODE || "fail-open") as FailMode;

const pkg = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "package.json"), "utf-8"));
const SYSTEM_VERSION = pkg.version;
const BUILD_TIMESTAMP = new Date().toISOString();

const sc = StringCodec();

// État applicatif (state.ts) + alerting/checks SLO (slo.ts).
const state: AppState = createState({ quorum: QUORUM, failMode: FAIL_MODE });

// `broadcast` est défini ici pour être passable à createSLO ; il devient
// effectif une fois que le WebSocketServer est attaché plus bas.
let _wss: WebSocketServer | null = null;
function broadcast(obj: any): void {
  if (!_wss) return;
  const msg = JSON.stringify(obj);
  for (const client of _wss.clients) if (client.readyState === WebSocket.OPEN) client.send(msg);
}

const slo: SLO = createSLO(state, broadcast, {
  iocFloodThreshold: IOC_FLOOD_THRESHOLD,
  maxBlocklistEntries: MAX_BLOCKLIST_ENTRIES,
});

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

_wss = new WebSocketServer({ server: httpServer });
const wss = _wss;

// Référence partagée vers la connexion NATS — assignée par le bloc
// async d'initialisation plus bas. Les commands.ts y accède via getter.
let natsConnection: any = null;

attachWSCommands(wss, {
  state,
  sc,
  broadcast,
  getNatsConnection: () => natsConnection,
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
    slo.checkNodeIsolation();
    slo.checkIOCFlood();
    slo.updateSLOMetrics();
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
  
  // updateSLOMetrics calcule MTTD/MTTR/containment* et déclenche les checks
  slo.updateSLOMetrics();
  
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
        slo.checkContainmentBreach(alert.iocKey);
      }

      // Check blocklist saturation
      slo.checkBlocklistSaturation(id);
      
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
      
      if (!state.votes.has(k)) state.votes.set(k, new Set());
      state.votes.get(k)!.add(ioc.source);
        broadcast({type:"event", payload:{kind:"ioc.local", iocKind: ioc.kind, value: ioc.value, reason: ioc.reason, source: ioc.source, confidence: ioc.confidence, ttl_sec: ioc.ttl_sec, firstSeen: ioc.firstSeen}});

      // Use weighted quorum based on reputation
      const weightedVotes = calculateWeightedQuorum(state, ioc);
      const requiredQuorum = state.globalQuorum;

      if (weightedVotes >= requiredQuorum || state.votes.get(k)!.size >= QUORUM){
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
            slo.addSLOAlert({
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
