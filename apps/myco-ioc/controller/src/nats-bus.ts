// Subscriptions NATS du controller : toutes les routes du bus mycélien.
// Découplé du transport HTTP — reçoit broadcast et slo en deps.

import { Codec, NatsConnection } from "nats";
import { log } from "./log";
import {
  AppState,
  IOC,
  defaultNode,
  keyOf,
  calculateWeightedQuorum,
} from "./state";
import { SLO } from "./slo";

export type BusDeps = {
  state: AppState;
  slo: SLO;
  broadcast: (msg: any) => void;
  sc: Codec<string>;
  defaultTtlSec: number;
  baseQuorum: number;
};

export async function subscribeAll(nc: NatsConnection, deps: BusDeps): Promise<void> {
  const { state, slo, broadcast, sc, defaultTtlSec, baseQuorum } = deps;

  // Synchronisation des IOCs actifs pour les nouveaux nodes (req/reply)
  await nc.subscribe("ioc.sync.request", {
    callback: async (_e, m) => {
      try {
        const request = JSON.parse(sc.decode(m.data));
        const nodeId = request.nodeId || "unknown";
        const now = Date.now();

        const activeIOCsList = Array.from(state.activeIOCs.entries())
          .filter(([, ioc]) => now < ioc.endTime)
          .map(([, ioc]) => ({
            kind: ioc.kind,
            value: ioc.value,
            reason: ioc.reason,
            source: ioc.source,
            confidence: ioc.confidence,
            ttl_sec: Math.max(0, Math.floor((ioc.endTime - now) / 1000)),
            firstSeen: ioc.firstSeen,
          }));

        if (m.reply) {
          nc.publish(m.reply, sc.encode(JSON.stringify({ nodeId, iocs: activeIOCsList, timestamp: now })));
          log.info("sent active IOCs for sync", { node_id: nodeId, count: activeIOCsList.length });
        }
      } catch (err) {
        log.error("error handling IOC sync request", { err });
      }
    },
  });

  // Présence + heartbeat
  await nc.subscribe("nodes.hello", {
    callback: (_e, m) => {
      try {
        const hello = JSON.parse(sc.decode(m.data));
        const id = hello.nodeId || "unknown";
        const now = Date.now();
        const n = state.nodes.get(id) || defaultNode(id, now);
        n.lastSeen = now;
        n.lastHeartbeat = now;
        state.nodes.set(id, n);
        broadcast({ type: "event", payload: { kind: "hello", nodeId: id, ts: hello.ts } });
      } catch { /* ignore */ }
    },
  });

  // Alerts
  await nc.subscribe("alerts.*", {
    callback: (_e, m) => {
      const alert = JSON.parse(sc.decode(m.data));
      const id = alert.nodeId;
      const now = Date.now();
      const n = state.nodes.get(id) || defaultNode(id, now);
      n.alerts++;
      n.alerts_1m++;
      n.lastSeen = now;
      state.nodes.set(id, n);

      if (!state.metrics.firstAlert.has(id)) {
        state.metrics.firstAlert.set(id, now);
      }
      // Simulé : on n'a pas accès au timing réel du burst, on prend ~5s avant.
      if (!state.metrics.firstOffensiveEvent.has(id)) {
        state.metrics.firstOffensiveEvent.set(id, now - Math.random() * 5000);
      }

      if (alert.iocKey) slo.checkContainmentBreach(alert.iocKey);
      slo.checkBlocklistSaturation(id);

      broadcast({ type: "event", payload: { kind: "alert", ...alert } });
    },
  });

  // Drops
  await nc.subscribe("drops.*", {
    callback: (_e, m) => {
      const drop = JSON.parse(sc.decode(m.data));
      const id = drop.nodeId;
      const now = Date.now();
      const n = state.nodes.get(id) || defaultNode(id, now);
      n.drops++;
      n.drops_1m++;
      n.lastSeen = now;
      state.nodes.set(id, n);

      if (drop.iocKey) state.metrics.lastDropByIOC.set(drop.iocKey, now);
      broadcast({ type: "event", payload: { kind: "drop", ...drop } });
    },
  });

  // IOC local -> quorum -> share
  await nc.subscribe("ioc.local", {
    callback: (_e, m) => {
      const ioc = JSON.parse(sc.decode(m.data)) as IOC;
      const k = keyOf(ioc);
      const now = Date.now();

      state.iocLocalWindow.push(now);

      if (!state.votes.has(k)) state.votes.set(k, new Set());
      state.votes.get(k)!.add(ioc.source);

      broadcast({
        type: "event",
        payload: {
          kind: "ioc.local",
          iocKind: ioc.kind,
          value: ioc.value,
          reason: ioc.reason,
          source: ioc.source,
          confidence: ioc.confidence,
          ttl_sec: ioc.ttl_sec,
          firstSeen: ioc.firstSeen,
        },
      });

      const weightedVotes = calculateWeightedQuorum(state, ioc);
      if (weightedVotes >= state.globalQuorum || state.votes.get(k)!.size >= baseQuorum) {
        const shared = { ...ioc, ttl_sec: defaultTtlSec };
        state.activeIOCs.set(k, {
          ...shared,
          startTime: now,
          endTime: now + shared.ttl_sec * 1000,
        });

        if (!state.metrics.firstIOCShare.has(k)) {
          state.metrics.firstIOCShare.set(k, now);
        }

        nc.publish("ioc.share", sc.encode(JSON.stringify(shared)));
        broadcast({
          type: "event",
          payload: {
            kind: "ioc.share",
            iocKind: shared.kind,
            value: shared.value,
            reason: shared.reason,
            source: shared.source,
            confidence: shared.confidence,
            ttl_sec: shared.ttl_sec,
            firstSeen: shared.firstSeen,
          },
        });
      }
    },
  });

  // IOC acknowledgments
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
          if (node.health === "ok") node.health = "protected";

          const ioc = state.activeIOCs.get(iocKey);
          if (ioc && now - ioc.startTime > 10_000) {
            slo.addSLOAlert({
              type: "sync_ko",
              nodeId,
              iocKey,
              timestamp: now,
              severity: "warning",
              message: `Nœud ${nodeId} n'applique pas l'IOC ${iocKey}`,
            });
          }
        }

        broadcast({ type: "event", payload: { kind: "ack", nodeId, iocKey, ts: ack.ts } });
      } catch { /* ignore */ }
    },
  });
}

export function startMetricsPublisher(nc: NatsConnection, state: AppState, sc: Codec<string>, intervalMs = 10_000): NodeJS.Timeout {
  return setInterval(() => {
    const metrics = {
      mttd: state.sloMetrics.mttd,
      mttr: state.sloMetrics.mttr,
      coverage: state.sloMetrics.coverage,
      containmentRatio: state.sloMetrics.containmentRatio,
      ioc_rate: state.sloMetrics.iocRate,
      timestamp: Date.now(),
    };
    nc.publish("metrics.controller", sc.encode(JSON.stringify(metrics)));
  }, intervalMs);
}
