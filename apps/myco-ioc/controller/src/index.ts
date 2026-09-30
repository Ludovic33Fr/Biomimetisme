// controller/src/index.ts — orchestration uniquement.
// La logique vit dans state.ts, slo.ts, commands.ts, nats-bus.ts, http-server.ts.

import { connect, StringCodec, NatsConnection } from "nats";
import { WebSocketServer } from "ws";
import * as fs from "fs";
import * as path from "path";
import { log } from "./log";
import { createState, recomputeHealth, FENETRE_DETECTION_MS, FailMode } from "./state";
import {
  createSLO,
  SLO_MTTD_MAX,
  SLO_MTTR_MAX,
  SLO_CONTAINMENT_MAX,
  NODE_ISOLATION_TIMEOUT,
} from "./slo";
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

/** Arrondit une métrique, en laissant passer « non mesuré » (null). */
function arrondi(valeur: number | null, diviseur = 1): number | null {
  return valeur == null ? null : Math.round(valeur / diviseur);
}

// Broadcast périodique de l'état complet vers les clients WS.
//
// Le payload est la *seule* source de vérité de l'UI : tout ce que les
// dashboards affichent doit venir d'ici. Pas de valeur reconstituée ni
// inventée côté navigateur.
setInterval(() => {
  const now = Date.now();
  const activeKeys = Array.from(state.activeIOCs.keys());

  // Un nœud protégé est un nœud qui a acquitté un IOC encore actif.
  recomputeHealth(state);

  // Recalcule MTTD/MTTR/containment/couverture + check violations.
  slo.updateSLOMetrics();

  const nodes = Array.from(state.nodes.values()).map(node => ({
    id: node.id,
    health: node.health,
    // « Vient de détecter » : un arbre qui a levé une alerte dans la fenêtre
    // récente. Passé ce délai il redevient un arbre protégé comme les autres,
    // ce qui est précisément la leçon de la démo.
    isDetector: node.lastAlertAt > 0 && now - node.lastAlertAt < FENETRE_DETECTION_MS,
    alerts: node.alerts,
    drops: node.drops,
    alerts_1m: node.alerts_1m || 0,
    drops_1m: node.drops_1m || 0,
    lastSeen: node.lastSeen || now,
    lastHeartbeat: node.lastHeartbeat || now,
    blocklistEntries: node.blocklistEntries || 0,
    reputation: state.nodeReputation.get(node.id) ?? 0.5,
    // Map -> tableau : une Map se sérialise en {} en JSON.
    appliedIOCs: activeKeys.filter(key => node.iocAcks.has(key)),
  }));

  const activeIOCs = Array.from(state.activeIOCs.entries()).map(([key, ioc]) => {
    const voters = Array.from(state.votes.get(key) ?? []);
    const acks = Array.from(state.nodes.values())
      .filter(n => n.iocAcks.has(key))
      .map(n => n.id);
    return {
      key,
      kind: ioc.kind,
      value: ioc.value,
      reason: ioc.reason,
      source: ioc.source,
      confidence: ioc.confidence,
      firstSeen: ioc.firstSeen,
      startTime: ioc.startTime,
      endTime: ioc.endTime,
      ttl_sec: ioc.ttl_sec,
      ttlRemainingSec: Math.max(0, Math.round((ioc.endTime - now) / 1000)),
      voters,
      voteCount: voters.length,
      quorumRequired: state.globalQuorum,
      acks,
      ackCount: acks.length,
    };
  });

  broadcast({
    type: "state",
    payload: {
      nodes,
      activeIOCs,
      totalNodes: state.nodes.size,
      metrics: {
        // null traverse tel quel : l'UI affiche « — », pas « 0 ms ».
        mttd: arrondi(state.sloMetrics.mttd),
        mttr: arrondi(state.sloMetrics.mttr),
        // En millisecondes comme mttd/mttr : une seule unité, un seul
        // formateur côté UI (un confinement sub-seconde s'affichait « 0 s »).
        containmentTime: arrondi(state.sloMetrics.containmentTime),
        containmentRatio: Math.round(state.sloMetrics.containmentRatio),
        iocRate: Math.round(state.sloMetrics.iocRate * 10) / 10,
        coverage: Math.round(state.sloMetrics.coverage),
        coverageDetail: state.sloMetrics.coverageDetail,
        sloViolations: state.sloMetrics.sloViolations,
        consecutiveViolations: state.sloMetrics.consecutiveViolations,
      },
      // Seuils : l'UI les affiche au lieu de les coder en dur de son côté.
      thresholds: {
        mttdMaxMs: SLO_MTTD_MAX,
        mttrMaxMs: SLO_MTTR_MAX,
        containmentMaxMs: SLO_CONTAINMENT_MAX,
        isolationTimeoutMs: NODE_ISOLATION_TIMEOUT,
        iocFloodPerSec: IOC_FLOOD_THRESHOLD,
      },
      sloAlerts: state.sloAlerts.slice(0, 10),
      globalQuorum: state.globalQuorum,
      floodMode: state.floodMode,
      controllerHealth: state.controllerHealth,
      failMode: state.failMode,
      systemVersion: SYSTEM_VERSION,
      buildTimestamp: BUILD_TIMESTAMP,
      timestamp: now,
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
