// State + types + reducers purs.
// Ne dépend ni de NATS, ni de WebSocket, ni du HTTP — testable en isolation.

export type IOC = {
  kind: "ip" | "ua" | "path";
  value: string;
  reason: string;
  source: string;
  confidence: number;
  ttl_sec: number;
  firstSeen: number;
};

export type ActiveIOC = IOC & { startTime: number; endTime: number };

export type NodeHealth = "ok" | "protected" | "isolated";

export type NodeState = {
  id: string;
  alerts: number;
  drops: number;
  health: NodeHealth;
  lastSeen: number;
  alerts_1m: number;
  drops_1m: number;
  lastHeartbeat: number;
  blocklistEntries: number;
  iocAcks: Map<string, number>;
  reputation: number;
};

export type SLOAlert = {
  type:
    | "reactivity_degraded"
    | "containment_breach"
    | "ioc_flood"
    | "node_isolated"
    | "sync_ko"
    | "blocklist_saturation";
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

export type SLOMetrics = {
  mttd: number;
  mttr: number;
  containmentTime: number;
  containmentRatio: number;
  iocRate: number;
  coverage: number;
  sloViolations: { mttd: number; mttr: number; containment: number };
  consecutiveViolations: { mttd: number; mttr: number; containment: number };
};

export type ControllerHealth = "healthy" | "degraded" | "critical";
export type FailMode = "fail-open" | "fail-closed";

export type AppState = {
  nodes: Map<string, NodeState>;
  events: any[];
  activeIOCs: Map<string, ActiveIOC>;
  votes: Map<string, Set<string>>;
  metrics: {
    mttd: number;
    mttr: number;
    containmentTime: number;
    containmentRatio: number;
    firstOffensiveEvent: Map<string, number>;
    firstAlert: Map<string, number>;
    firstIOCShare: Map<string, number>;
    lastDropByIOC: Map<string, number>;
  };
  nodeReputation: Map<string, number>;
  globalQuorum: number;
  baseQuorum: number;
  sloAlerts: SLOAlert[];
  sloMetrics: SLOMetrics;
  iocLocalCount: number;
  iocLocalWindow: number[];
  floodMode: boolean;
  containmentBreaches: Map<string, number>;
  controllerHealth: ControllerHealth;
  failMode: FailMode;
};

export type CreateStateOpts = {
  quorum: number;
  failMode: FailMode;
};

export function createState(opts: CreateStateOpts): AppState {
  return {
    nodes: new Map(),
    events: [],
    activeIOCs: new Map(),
    votes: new Map(),
    metrics: {
      mttd: 0,
      mttr: 0,
      containmentTime: 0,
      containmentRatio: 0,
      firstOffensiveEvent: new Map(),
      firstAlert: new Map(),
      firstIOCShare: new Map(),
      lastDropByIOC: new Map(),
    },
    nodeReputation: new Map(),
    globalQuorum: opts.quorum,
    baseQuorum: opts.quorum,
    sloAlerts: [],
    sloMetrics: {
      mttd: 0,
      mttr: 0,
      containmentTime: 0,
      containmentRatio: 0,
      iocRate: 0,
      coverage: 0,
      sloViolations: { mttd: 0, mttr: 0, containment: 0 },
      consecutiveViolations: { mttd: 0, mttr: 0, containment: 0 },
    },
    iocLocalCount: 0,
    iocLocalWindow: [],
    floodMode: false,
    containmentBreaches: new Map(),
    controllerHealth: "healthy",
    failMode: opts.failMode,
  };
}

export function defaultNode(id: string, ts: number): NodeState {
  return {
    id,
    alerts: 0,
    drops: 0,
    health: "ok",
    lastSeen: ts,
    alerts_1m: 0,
    drops_1m: 0,
    lastHeartbeat: ts,
    blocklistEntries: 0,
    iocAcks: new Map(),
    reputation: 0.5,
  };
}

export function keyOf(ioc: Pick<IOC, "kind" | "value">): string {
  return `${ioc.kind}|${ioc.value}`;
}

export function updateNodeReputation(state: AppState, nodeId: string, isFalsePositive: boolean): void {
  const current = state.nodeReputation.get(nodeId) ?? 0.5;
  const adjustment = isFalsePositive ? -0.1 : 0.05;
  const next = Math.max(0, Math.min(1, current + adjustment));
  state.nodeReputation.set(nodeId, next);
}

export function calculateWeightedQuorum(state: AppState, ioc: IOC): number {
  const sources = state.votes.get(keyOf(ioc)) || new Set<string>();
  let weighted = 0;
  for (const source of sources) {
    weighted += state.nodeReputation.get(source) ?? 0.5;
  }
  return weighted;
}

export function calculateMTTD(state: AppState): number {
  let total = 0;
  let count = 0;
  for (const [nodeId, firstOffensive] of state.metrics.firstOffensiveEvent) {
    const firstAlert = state.metrics.firstAlert.get(nodeId);
    if (firstAlert && firstAlert > firstOffensive) {
      total += firstAlert - firstOffensive;
      count++;
    }
  }
  return count > 0 ? total / count : 0;
}

export function calculateMTTR(state: AppState): number {
  let total = 0;
  let count = 0;
  for (const [nodeId, firstAlert] of state.metrics.firstAlert) {
    const firstShare = state.metrics.firstIOCShare.get(nodeId);
    if (firstShare && firstShare > firstAlert) {
      total += firstShare - firstAlert;
      count++;
    }
  }
  return count > 0 ? total / count : 0;
}

export function calculateContainmentTime(state: AppState): number {
  let total = 0;
  let count = 0;
  for (const [iocKey, firstShare] of state.metrics.firstIOCShare) {
    const lastDrop = state.metrics.lastDropByIOC.get(iocKey);
    if (lastDrop && lastDrop > firstShare) {
      total += lastDrop - firstShare;
      count++;
    }
  }
  return count > 0 ? total / count : 0;
}

export function calculateContainmentRatio(state: AppState): number {
  const totalNodes = state.nodes.size;
  if (totalNodes === 0) return 0;

  let nodesNeverAlertedAfterIOC = 0;
  for (const [nodeId] of state.nodes) {
    const firstAlert = state.metrics.firstAlert.get(nodeId);
    if (!firstAlert) continue;

    let hasIOCAfterAlert = false;
    for (const [, firstShare] of state.metrics.firstIOCShare) {
      if (firstShare > firstAlert) {
        hasIOCAfterAlert = true;
        break;
      }
    }
    if (!hasIOCAfterAlert) nodesNeverAlertedAfterIOC++;
  }
  return (nodesNeverAlertedAfterIOC / totalNodes) * 100;
}
