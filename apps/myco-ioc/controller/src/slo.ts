// SLO/SLA — calculs, alertes et checks périodiques.
// Dépend de l'état (state.ts) et d'une fonction broadcast (injectée).

import { log } from "./log";
import {
  AppState,
  SLOAlert,
  calculateMTTD,
  calculateMTTR,
  calculateContainmentTime,
  calculateContainmentRatio,
} from "./state";

export const SLO_MTTD_MAX = 2000; // ms
export const SLO_MTTR_MAX = 3000; // ms
export const SLO_CONTAINMENT_MAX = 10_000; // ms
export const NODE_ISOLATION_TIMEOUT = 15_000; // ms

export type Broadcast = (msg: any) => void;

export type SLOOpts = {
  iocFloodThreshold: number;
  maxBlocklistEntries: number;
};

export function createSLO(state: AppState, broadcast: Broadcast, opts: SLOOpts) {
  function addSLOAlert(alert: SLOAlert): void {
    state.sloAlerts.unshift(alert);
    if (state.sloAlerts.length > 100) state.sloAlerts.pop();

    broadcast({ type: "slo_alert", payload: alert });

    const logFn = alert.severity === "critical" ? log.error : log.warn;
    logFn("SLO alert", {
      type: alert.type,
      severity: alert.severity,
      message: alert.message,
      node_id: alert.nodeId,
      ioc_key: alert.iocKey,
    });
  }

  function checkSLOViolations(): void {
    const now = Date.now();
    const m = state.sloMetrics;

    // MTTD
    if (m.mttd > SLO_MTTD_MAX) {
      m.sloViolations.mttd++;
      m.consecutiveViolations.mttd++;
      if (m.consecutiveViolations.mttd >= 3) {
        addSLOAlert({
          type: "reactivity_degraded",
          timestamp: now,
          severity: "critical",
          message: `MTTD dégradé: ${Math.round(m.mttd)}ms (SLO: ${SLO_MTTD_MAX}ms)`,
          metrics: { mttd: m.mttd },
        });
        m.consecutiveViolations.mttd = 0;
      }
    } else {
      m.consecutiveViolations.mttd = 0;
    }

    // MTTR
    if (m.mttr > SLO_MTTR_MAX) {
      m.sloViolations.mttr++;
      m.consecutiveViolations.mttr++;
      if (m.consecutiveViolations.mttr >= 3) {
        addSLOAlert({
          type: "reactivity_degraded",
          timestamp: now,
          severity: "critical",
          message: `MTTR dégradé: ${Math.round(m.mttr)}ms (SLO: ${SLO_MTTR_MAX}ms)`,
          metrics: { mttr: m.mttr },
        });
        m.consecutiveViolations.mttr = 0;
      }
    } else {
      m.consecutiveViolations.mttr = 0;
    }

    // Containment
    if (m.containmentTime > SLO_CONTAINMENT_MAX) {
      m.sloViolations.containment++;
      m.consecutiveViolations.containment++;
      if (m.consecutiveViolations.containment >= 3) {
        addSLOAlert({
          type: "reactivity_degraded",
          timestamp: now,
          severity: "critical",
          message: `Containment dégradé: ${Math.round(m.containmentTime)}ms (SLO: ${SLO_CONTAINMENT_MAX}ms)`,
          metrics: { containment: m.containmentTime },
        });
        m.consecutiveViolations.containment = 0;
      }
    } else {
      m.consecutiveViolations.containment = 0;
    }
  }

  function checkNodeIsolation(): void {
    const now = Date.now();
    for (const [nodeId, node] of state.nodes) {
      const since = now - node.lastHeartbeat;
      if (since > NODE_ISOLATION_TIMEOUT) {
        if (node.health !== "isolated") {
          node.health = "isolated";
          addSLOAlert({
            type: "node_isolated",
            nodeId,
            timestamp: now,
            severity: "critical",
            message: `Nœud ${nodeId} isolé depuis ${Math.round(since / 1000)}s`,
          });
        }
      } else if (node.health === "isolated") {
        node.health = "ok";
      }
    }
  }

  function checkIOCFlood(): void {
    const now = Date.now();
    const windowStart = now - 1000;
    state.iocLocalWindow = state.iocLocalWindow.filter(ts => ts > windowStart);
    const rate = state.iocLocalWindow.length;
    state.sloMetrics.iocRate = rate;

    if (rate > opts.iocFloodThreshold && !state.floodMode) {
      state.floodMode = true;
      state.globalQuorum = Math.min(state.globalQuorum + 1, 10);
      addSLOAlert({
        type: "ioc_flood",
        timestamp: now,
        severity: "warning",
        message: `IOC flood détecté: ${rate} IOC/s (seuil: ${opts.iocFloodThreshold})`,
        metrics: { iocRate: rate },
      });
      log.warn("IOC flood mode activated", { quorum: state.globalQuorum });
    } else if (rate <= opts.iocFloodThreshold / 2 && state.floodMode) {
      state.floodMode = false;
      state.globalQuorum = Math.max(state.globalQuorum - 1, state.baseQuorum);
      addSLOAlert({
        type: "ioc_flood",
        timestamp: now,
        severity: "warning",
        message: `IOC flood résolu - Retour au quorum normal: ${state.globalQuorum}`,
        metrics: { iocRate: rate },
      });
    }
  }

  function checkContainmentBreach(iocKey: string): void {
    const prev = state.containmentBreaches.get(iocKey) ?? 0;
    const next = prev + 1;
    state.containmentBreaches.set(iocKey, next);

    if (next >= state.globalQuorum) {
      addSLOAlert({
        type: "containment_breach",
        iocKey,
        timestamp: Date.now(),
        severity: "critical",
        message: `IOC ${iocKey} inefficace - ${next} nœuds déclenchent encore des alertes`,
      });
      state.globalQuorum = Math.min(state.globalQuorum + 1, 10);
      log.warn("containment breach - escalated quorum", { quorum: state.globalQuorum });
    }
  }

  function checkBlocklistSaturation(nodeId: string): void {
    const node = state.nodes.get(nodeId);
    if (node && node.blocklistEntries >= opts.maxBlocklistEntries) {
      addSLOAlert({
        type: "blocklist_saturation",
        nodeId,
        timestamp: Date.now(),
        severity: "warning",
        message: `Nœud ${nodeId} - Blocklist saturée (${node.blocklistEntries}/${opts.maxBlocklistEntries})`,
      });
      log.warn("blocklist saturation - LRU recommended", { node_id: nodeId });
    }
  }

  function updateSLOMetrics(): void {
    state.metrics.mttd = calculateMTTD(state);
    state.metrics.mttr = calculateMTTR(state);
    state.metrics.containmentTime = calculateContainmentTime(state);
    state.metrics.containmentRatio = calculateContainmentRatio(state);

    state.sloMetrics.mttd = state.metrics.mttd;
    state.sloMetrics.mttr = state.metrics.mttr;
    state.sloMetrics.containmentTime = state.metrics.containmentTime;
    state.sloMetrics.containmentRatio = state.metrics.containmentRatio;

    const nodesWithIOC = Array.from(state.nodes.values()).filter(n => n.alerts > 0).length;
    state.sloMetrics.coverage =
      state.nodes.size > 0 ? (nodesWithIOC / state.nodes.size) * 100 : 0;

    checkSLOViolations();
  }

  return {
    addSLOAlert,
    checkSLOViolations,
    checkNodeIsolation,
    checkIOCFlood,
    checkContainmentBreach,
    checkBlocklistSaturation,
    updateSLOMetrics,
  };
}

export type SLO = ReturnType<typeof createSLO>;
