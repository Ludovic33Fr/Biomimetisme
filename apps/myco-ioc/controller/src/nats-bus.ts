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

  // Présence + heartbeat.
  //
  // `nodes.hello` arrive toutes les 5 s et par nœud : c'est un battement de
  // cœur, pas un événement. Le rediffuser tel quel noyait le journal sous des
  // « a rejoint le mycélium » pour des arbres qui n'avaient pas bougé. On
  // n'émet donc un événement que sur un vrai changement de présence.
  await nc.subscribe("nodes.hello", {
    callback: (_e, m) => {
      try {
        const hello = JSON.parse(sc.decode(m.data));
        const id = hello.nodeId || "unknown";
        const now = Date.now();

        const connu = state.nodes.get(id);
        const revient = connu?.health === "isolated";

        const n = connu || defaultNode(id, now);
        n.lastSeen = now;
        n.lastHeartbeat = now;
        if (revient) n.health = "ok";
        state.nodes.set(id, n);

        if (!connu) {
          broadcast({ type: "event", payload: { kind: "hello", nodeId: id, ts: hello.ts || now } });
        } else if (revient) {
          broadcast({
            type: "event",
            payload: { kind: "node_reconnected", nodeId: id, ts: hello.ts || now },
          });
        }
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
      n.lastAlertAt = now;
      state.nodes.set(id, n);

      if (!state.metrics.firstAlert.has(id)) {
        state.metrics.firstAlert.set(id, now);
      }
      // Le node envoie le début réel du burst. S'il ne le fournit pas (vieux
      // node), on n'enregistre rien : le MTTD restera « non mesuré » plutôt
      // que d'être rempli avec une valeur inventée.
      if (!state.metrics.firstOffensiveEvent.has(id) && typeof alert.firstEventTs === "number") {
        state.metrics.firstOffensiveEvent.set(id, alert.firstEventTs);
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
          iocKey: k,
          iocKind: ioc.kind,
          value: ioc.value,
          reason: ioc.reason,
          source: ioc.source,
          confidence: ioc.confidence,
          ttl_sec: ioc.ttl_sec,
          firstSeen: ioc.firstSeen,
          // Le vote est le mécanisme central : l'UI doit pouvoir montrer
          // « 2 voix sur 3 requises » avant même que l'IOC soit partagé.
          voteCount: state.votes.get(k)!.size,
          quorumRequired: state.globalQuorum,
          ts: now,
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
        // Indexé aussi par nœud source : le MTTR compare l'alerte d'un nœud
        // au partage qui en découle, et ces deux maps doivent partager la
        // même clé (le bug précédent croisait un nodeId avec une clé d'IOC,
        // donc le MTTR valait toujours 0).
        if (!state.metrics.firstIOCShareByNode.has(shared.source)) {
          state.metrics.firstIOCShareByNode.set(shared.source, now);
        }

        nc.publish("ioc.share", sc.encode(JSON.stringify(shared)));
        broadcast({
          type: "event",
          payload: {
            kind: "ioc.share",
            iocKey: k,
            iocKind: shared.kind,
            value: shared.value,
            reason: shared.reason,
            source: shared.source,
            confidence: shared.confidence,
            ttl_sec: shared.ttl_sec,
            firstSeen: shared.firstSeen,
            voteCount: state.votes.get(k)!.size,
            quorumRequired: state.globalQuorum,
            // Destinataires de la propagation, pour l'animer arête par arête.
            targets: Array.from(state.nodes.keys()).filter(id => id !== shared.source),
            ts: now,
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
          // La santé est recalculée à chaque broadcast depuis les acks +
          // les IOCs encore actifs (cf. recomputeHealth) : ne pas la forcer ici.

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
