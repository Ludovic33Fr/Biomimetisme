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
  // Dernière détection locale. « A détecté » est un événement, pas un état :
  // sans borne de temps, tous les arbres finissent marqués détecteurs et la
  // distinction avec « vacciné par le réseau » disparaît.
  lastAlertAt: number;
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
  mttd: number | null;
  mttr: number | null;
  containmentTime: number | null;
  containmentRatio: number;
  iocRate: number;
  coverage: number;
  coverageDetail: { protectedNodes: number; totalNodes: number; activeIOCs: number };
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
    // null = pas encore mesuré. Zéro voudrait dire « instantané », ce qui est
    // une affirmation, et fausse.
    mttd: number | null;
    mttr: number | null;
    containmentTime: number | null;
    containmentRatio: number;
    firstOffensiveEvent: Map<string, number>;
    firstAlert: Map<string, number>;
    firstIOCShare: Map<string, number>;
    firstIOCShareByNode: Map<string, number>;
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
      mttd: null,
      mttr: null,
      containmentTime: null,
      containmentRatio: 0,
      firstOffensiveEvent: new Map(),
      firstAlert: new Map(),
      firstIOCShare: new Map(),
      firstIOCShareByNode: new Map(),
    },
    nodeReputation: new Map(),
    globalQuorum: opts.quorum,
    baseQuorum: opts.quorum,
    sloAlerts: [],
    sloMetrics: {
      mttd: null,
      mttr: null,
      containmentTime: null,
      containmentRatio: 0,
      iocRate: 0,
      coverage: 0,
      coverageDetail: { protectedNodes: 0, totalNodes: 0, activeIOCs: 0 },
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
    lastAlertAt: 0,
  };
}

/** Durée pendant laquelle un arbre reste affiché comme « vient de détecter ». */
export const FENETRE_DETECTION_MS = 20_000;

/**
 * Remet le mycélium à l'état initial : aucune protection, aucune mesure,
 * aucun historique. Les nœuds restent enregistrés (ils sont vivants) mais
 * leurs compteurs et leur réputation repartent de zéro.
 *
 * Ne touche pas aux blocklists locales des nœuds : elles vivent dans leur
 * mémoire et se vident via le message `ioc.flush` (cf. commands.ts).
 */
export function resetDemoState(state: AppState, silenceMs = 15_000): void {
  // Un arbre qui ne donne plus signe de vie n'a rien à faire dans un état
  // « zéro » : c'est typiquement un conteneur arrêté par un scale down.
  const limite = Date.now() - silenceMs;
  for (const [id, node] of state.nodes) {
    if (node.lastHeartbeat < limite) state.nodes.delete(id);
  }

  state.activeIOCs.clear();
  state.votes.clear();
  state.sloAlerts.length = 0;
  state.events.length = 0;

  state.metrics.mttd = null;
  state.metrics.mttr = null;
  state.metrics.containmentTime = null;
  state.metrics.containmentRatio = 0;
  state.metrics.firstOffensiveEvent.clear();
  state.metrics.firstAlert.clear();
  state.metrics.firstIOCShare.clear();
  state.metrics.firstIOCShareByNode.clear();

  state.sloMetrics.mttd = null;
  state.sloMetrics.mttr = null;
  state.sloMetrics.containmentTime = null;
  state.sloMetrics.containmentRatio = 0;
  state.sloMetrics.iocRate = 0;
  state.sloMetrics.coverage = 0;
  state.sloMetrics.coverageDetail = { protectedNodes: 0, totalNodes: 0, activeIOCs: 0 };
  state.sloMetrics.sloViolations = { mttd: 0, mttr: 0, containment: 0 };
  state.sloMetrics.consecutiveViolations = { mttd: 0, mttr: 0, containment: 0 };

  state.nodeReputation.clear();
  state.iocLocalCount = 0;
  state.iocLocalWindow.length = 0;
  state.floodMode = false;
  state.containmentBreaches.clear();
  state.globalQuorum = state.baseQuorum;
  state.controllerHealth = "healthy";

  const maintenant = Date.now();
  for (const node of state.nodes.values()) {
    node.alerts = 0;
    node.drops = 0;
    node.alerts_1m = 0;
    node.drops_1m = 0;
    node.blocklistEntries = 0;
    node.iocAcks.clear();
    node.reputation = 0.5;
    node.lastAlertAt = 0;
    node.health = "ok";
    // On ne touche pas à lastSeen/lastHeartbeat : le nœud est bien vivant,
    // le remettre à zéro le ferait passer pour isolé.
    node.lastSeen = node.lastSeen || maintenant;
  }
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

/** Temps entre le premier paquet offensif et l'alerte locale. */
export function calculateMTTD(state: AppState): number | null {
  let total = 0;
  let count = 0;
  for (const [nodeId, firstOffensive] of state.metrics.firstOffensiveEvent) {
    const firstAlert = state.metrics.firstAlert.get(nodeId);
    // >= et non > : deux événements traités dans la même milliseconde donnent
    // une mesure de 0 ms, ce qui est un résultat, pas une absence de résultat.
    if (firstAlert && firstAlert >= firstOffensive) {
      total += firstAlert - firstOffensive;
      count++;
    }
  }
  return count > 0 ? total / count : null;
}

/** Temps entre l'alerte d'un nœud et le partage de l'IOC qui en découle. */
export function calculateMTTR(state: AppState): number | null {
  let total = 0;
  let count = 0;
  for (const [nodeId, firstAlert] of state.metrics.firstAlert) {
    const firstShare = state.metrics.firstIOCShareByNode.get(nodeId);
    // Le node publie alerts.<id> puis ioc.local dans la foulée : le controller
    // traite souvent les deux dans la même milliseconde. Avec un « > » strict,
    // le MTTR n'était jamais mesuré.
    if (firstShare && firstShare >= firstAlert) {
      total += firstShare - firstAlert;
      count++;
    }
  }
  return count > 0 ? total / count : null;
}

/**
 * Temps entre le partage d'un IOC et son application par le dernier arbre,
 * mesuré sur les ACK.
 *
 * Deux définitions ont été écartées, toutes deux non bornées :
 *  - « jusqu'au dernier blocage » grandit tant que l'attaquant insiste ;
 *  - « jusqu'au premier blocage de chaque arbre » dépend de l'instant où
 *    l'attaquant daigne visiter cet arbre, pas de la vitesse du mycélium.
 * L'ACK est le seul signal qui dise « la protection est installée ici ».
 */
export function calculateContainmentTime(state: AppState): number | null {
  let total = 0;
  let count = 0;

  for (const [iocKey, firstShare] of state.metrics.firstIOCShare) {
    let dernierAck = 0;
    for (const node of state.nodes.values()) {
      const ts = node.iocAcks.get(iocKey);
      if (ts != null && ts >= firstShare) dernierAck = Math.max(dernierAck, ts);
    }
    if (dernierAck > 0) {
      total += dernierAck - firstShare;
      count++;
    }
  }

  return count > 0 ? total / count : null;
}

/**
 * Un nœud est « protégé » s'il a acquitté au moins un IOC encore actif.
 * Recalculé à chaque broadcast : sans ça, un nœud reste bleu indéfiniment
 * alors que l'IOC qui le protégeait a expiré depuis longtemps.
 *
 * L'isolation (pas de heartbeat) l'emporte sur tout le reste. Le nœud
 * détecteur n'est jamais isolé de ce fait — comportement volontaire.
 */
export function recomputeHealth(state: AppState): void {
  const activeKeys = new Set(state.activeIOCs.keys());
  for (const node of state.nodes.values()) {
    if (node.health === "isolated") continue;
    let protectedByActiveIOC = false;
    for (const key of activeKeys) {
      if (node.iocAcks.has(key)) {
        protectedByActiveIOC = true;
        break;
      }
    }
    node.health = protectedByActiveIOC ? "protected" : "ok";
  }
}

/**
 * Part des nœuds qui appliquent *tous* les IOCs actifs.
 * Sans IOC actif il n'y a rien à propager : la couverture vaut 100 %.
 */
export function calculateCoverage(state: AppState): {
  coverage: number;
  protectedNodes: number;
  totalNodes: number;
  activeIOCs: number;
} {
  const totalNodes = state.nodes.size;
  const activeKeys = Array.from(state.activeIOCs.keys());

  if (totalNodes === 0) {
    return { coverage: 0, protectedNodes: 0, totalNodes: 0, activeIOCs: activeKeys.length };
  }
  if (activeKeys.length === 0) {
    return { coverage: 100, protectedNodes: totalNodes, totalNodes, activeIOCs: 0 };
  }

  let protectedNodes = 0;
  for (const node of state.nodes.values()) {
    if (activeKeys.every(key => node.iocAcks.has(key))) protectedNodes++;
  }
  return {
    coverage: (protectedNodes / totalNodes) * 100,
    protectedNodes,
    totalNodes,
    activeIOCs: activeKeys.length,
  };
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
