// controller/src/index.ts — orchestration uniquement.
// La logique vit dans state.ts, slo.ts, commands.ts, nats-bus.ts, http-server.ts.

import { connect, StringCodec, NatsConnection } from "nats";
import { WebSocketServer } from "ws";
import * as fs from "fs";
import * as path from "path";
import { log } from "./log";
import { createState, FailMode } from "./state";
import { createSLO } from "./slo";
import { attachWSCommands } from "./commands";
import { createBroadcaster, createHttpServer } from "./http-server";
import { subscribeAll, startMetricsPublisher } from "./nats-bus";

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

// État + SLO + broadcaster (pas encore lié au WS).
const state = createState({ quorum: QUORUM, failMode: FAIL_MODE });
const { broadcast, attachWss } = createBroadcaster();
const slo = createSLO(state, broadcast, {
  iocFloodThreshold: IOC_FLOOD_THRESHOLD,
  maxBlocklistEntries: MAX_BLOCKLIST_ENTRIES,
});

// HTTP + WebSocket.
const httpServer = createHttpServer({ state, version: SYSTEM_VERSION, buildTimestamp: BUILD_TIMESTAMP });
const wss = new WebSocketServer({ server: httpServer });
attachWss(wss);

// La connexion NATS est établie de manière async ; les commands.ts y
// accède via getter pour pouvoir être branchés avant la connexion.
let natsConnection: NatsConnection | null = null;

attachWSCommands(wss, {
  state,
  sc,
  broadcast,
  getNatsConnection: () => natsConnection,
});

// Boucles périodiques : nettoyage des IOCs expirés + checks SLO.
setInterval(() => {
  const now = Date.now();

  for (const [key, ioc] of state.activeIOCs) {
    if (now >= ioc.endTime) state.activeIOCs.delete(key);
  }

  // Reset des compteurs 1m chaque minute.
  if (now % 60_000 < 1000) {
    for (const node of state.nodes.values()) {
      node.alerts_1m = 0;
      node.drops_1m = 0;
    }
  }

  // Checks SLO toutes les 5s.
  if (now % 5000 < 1000) {
    slo.checkNodeIsolation();
    slo.checkIOCFlood();
    slo.updateSLOMetrics();
  }
}, 1000);

// Broadcast périodique de l'état complet vers les clients WS.
setInterval(() => {
  const nodesWithMetrics = Array.from(state.nodes.values()).map(node => ({
    ...node,
    alerts_1m: node.alerts_1m || 0,
    drops_1m: node.drops_1m || 0,
    lastSeen: node.lastSeen || Date.now(),
    lastHeartbeat: node.lastHeartbeat || Date.now(),
    blocklistEntries: node.blocklistEntries || 0,
    reputation: state.nodeReputation.get(node.id) || 0.5,
  }));

  // Recalcule MTTD/MTTR/containment + check violations en un appel.
  slo.updateSLOMetrics();

  broadcast({
    type: "state",
    payload: {
      nodes: nodesWithMetrics,
      activeIOCs: Array.from(state.activeIOCs.values()),
      metrics: {
        mttd: Math.round(state.metrics.mttd),
        mttr: Math.round(state.metrics.mttr),
        containmentTime: Math.round(state.metrics.containmentTime / 1000),
        containmentRatio: Math.round(state.metrics.containmentRatio),
      },
      sloMetrics: {
        mttd: Math.round(state.sloMetrics.mttd),
        mttr: Math.round(state.sloMetrics.mttr),
        containmentTime: Math.round(state.sloMetrics.containmentTime / 1000),
        containmentRatio: Math.round(state.sloMetrics.containmentRatio),
        iocRate: Math.round(state.sloMetrics.iocRate * 10) / 10,
        coverage: Math.round(state.sloMetrics.coverage),
        sloViolations: state.sloMetrics.sloViolations,
        consecutiveViolations: state.sloMetrics.consecutiveViolations,
      },
      sloAlerts: state.sloAlerts.slice(0, 10),
      globalQuorum: state.globalQuorum,
      floodMode: state.floodMode,
      controllerHealth: state.controllerHealth,
      failMode: state.failMode,
      systemVersion: SYSTEM_VERSION,
      buildTimestamp: BUILD_TIMESTAMP,
      timestamp: Date.now(),
    },
  });
}, 500);

// Boot : NATS + subscribers + HTTP listen.
(async () => {
  const nc = await connect({ servers: NATS_URL });
  natsConnection = nc;
  log.info("connected to NATS", { url: NATS_URL, http_port: HTTP_PORT, version: SYSTEM_VERSION });

  httpServer.listen(HTTP_PORT, () => {
    log.info("HTTP server listening", { port: HTTP_PORT });
  });

  await subscribeAll(nc, {
    state,
    slo,
    broadcast,
    sc,
    defaultTtlSec: DEFAULT_TTL,
    baseQuorum: QUORUM,
  });

  startMetricsPublisher(nc, state, sc);
})().catch(e => {
  log.error("fatal error", { err: e });
  process.exit(1);
});
