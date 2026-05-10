// Handlers WebSocket : commandes UI + handleTrafficControl.
// Découplé du transport (le NATS et le WSS sont passés en deps).

import WebSocket, { WebSocketServer } from "ws";
import { Codec, NatsConnection } from "nats";
import { log } from "./log";
import { AppState, defaultNode } from "./state";

export type CommandsDeps = {
  state: AppState;
  sc: Codec<string>;
  broadcast: (msg: any) => void;
  // natsConnection est récupérée via getter parce qu'elle est connectée
  // de manière asynchrone après le démarrage HTTP.
  getNatsConnection: () => NatsConnection | null;
};

function publish(deps: CommandsDeps, subject: string, payload: unknown): boolean {
  const nc = deps.getNatsConnection();
  if (!nc) return false;
  nc.publish(subject, deps.sc.encode(JSON.stringify(payload)));
  return true;
}

export function handleTrafficControl(
  deps: CommandsDeps,
  command: string,
  targetNodeId?: string,
): void {
  log.info("traffic control command", { command, target_node_id: targetNodeId });

  switch (command) {
    case "stop":
      if (publish(deps, "traffic.control", { action: "stop", timestamp: Date.now() })) {
        log.info("traffic stopped");
      }
      break;

    case "low":
      if (publish(deps, "traffic.control", { action: "low", interval: 2000, timestamp: Date.now() })) {
        log.info("traffic mode: low");
      }
      break;

    case "normal":
      if (publish(deps, "traffic.control", { action: "normal", interval: 60, timestamp: Date.now() })) {
        log.info("traffic mode: normal");
      }
      break;

    case "attack": {
      const nc = deps.getNatsConnection();
      if (!nc) break;
      const knownNodes = Array.from(deps.state.nodes.keys());
      const target = (targetNodeId && deps.state.nodes.has(targetNodeId))
        ? targetNodeId
        : knownNodes[Math.floor(Math.random() * knownNodes.length)];
      if (!target) {
        log.warn("no attack target available");
        break;
      }
      const badIP = "203.0.113.66";
      for (let i = 0; i < 30; i++) {
        setTimeout(() => {
          const ev = { nodeId: target, ts: Date.now(), src_ip: badIP, path: "/wp-login.php", status: 401, ua: "B4dB0t" };
          nc.publish("traffic.http", deps.sc.encode(JSON.stringify(ev)));
        }, i * 50);
      }
      log.info("attack triggered", { target_node_id: target });
      break;
    }

    default:
      log.warn("unknown traffic control command", { command });
  }
}

// Démo scriptée : chorégraphie d'attaque + propagation + retour au calme.
// Une seule démo à la fois (flag local au module).
let demoInProgress = false;

type DemoStep = {
  delayMs: number;
  message: string;
  action?: (deps: CommandsDeps) => void;
};

function emitNarration(deps: CommandsDeps, step: number, total: number, message: string): void {
  deps.broadcast({
    type: "narration",
    payload: { step, total, message, ts: Date.now() },
  });
  log.info("guided demo step", { step, total, message });
}

function pickAttackTargets(deps: CommandsDeps, n: number): string[] {
  const ids = Array.from(deps.state.nodes.keys());
  return ids.slice(0, n);
}

function fireAttack(deps: CommandsDeps, target: string, badIP = "203.0.113.66"): void {
  const nc = deps.getNatsConnection();
  if (!nc) return;
  for (let i = 0; i < 30; i++) {
    setTimeout(() => {
      const ev = { nodeId: target, ts: Date.now(), src_ip: badIP, path: "/wp-login.php", status: 401, ua: "B4dB0t" };
      nc.publish("traffic.http", deps.sc.encode(JSON.stringify(ev)));
    }, i * 50);
  }
}

export function runGuidedDemo(deps: CommandsDeps): void {
  if (demoInProgress) {
    log.warn("guided demo already in progress");
    return;
  }
  const targets = pickAttackTargets(deps, 3);
  if (targets.length === 0) {
    log.warn("guided demo aborted - no node available");
    return;
  }
  demoInProgress = true;

  const steps: DemoStep[] = [
    {
      delayMs: 0,
      message: "🌱 Démarrage : trafic légitime sur l'ensemble des nodes (mycélium au repos).",
      action: d => publish(d, "traffic.control", { action: "normal", interval: 60, timestamp: Date.now() }),
    },
    {
      delayMs: 5_000,
      message: "👀 Surveillance active. Chaque arbre observe son propre flux ; aucune anomalie pour l'instant.",
    },
    {
      delayMs: 10_000,
      message: `⚡ Attaque login-burst sur ${targets[0]} depuis 203.0.113.66 — le node détecte localement.`,
      action: d => fireAttack(d, targets[0]),
    },
    {
      delayMs: 15_000,
      message: "🌐 IOC partagé via le mycélium : le quorum est atteint, la liste de blocage se propage aux autres arbres.",
    },
    {
      delayMs: 25_000,
      message: targets.length > 1
        ? `🛡️ Test : attaque sur ${targets.slice(1).join(", ")} — bloqués dès le premier paquet (IOC déjà appliqué).`
        : "🛡️ IOC propagé, tous les nodes désormais protégés.",
      action: d => {
        for (const t of targets.slice(1)) fireAttack(d, t);
      },
    },
    {
      delayMs: 40_000,
      message: "🌙 Retour au calme : les IOCs entrent en TTL countdown, le trafic redevient normal.",
    },
    {
      delayMs: 50_000,
      message: "🐌 Réduction du trafic au minimum.",
      action: d => publish(d, "traffic.control", { action: "low", interval: 2000, timestamp: Date.now() }),
    },
    {
      delayMs: 60_000,
      message: "✅ Démo terminée. Tu peux relancer ou explorer librement.",
    },
  ];

  steps.forEach((step, i) => {
    setTimeout(() => {
      emitNarration(deps, i + 1, steps.length, step.message);
      if (step.action) step.action(deps);
      if (i === steps.length - 1) {
        demoInProgress = false;
      }
    }, step.delayMs);
  });
}

function handleWSMessage(deps: CommandsDeps, raw: string): void {
  const data = JSON.parse(raw);
  if (data.type !== "command") return;

  const { state, broadcast } = deps;

  switch (data.action) {
    case "updateQuorum":
      state.globalQuorum = data.value;
      log.info("global quorum updated", { quorum: data.value });
      break;

    case "expireIOC": {
      const iocKey = `${data.kind}|${data.value}`;
      state.activeIOCs.delete(iocKey);
      log.info("IOC expired", { ioc_key: iocKey });
      break;
    }

    case "extendIOC": {
      const extendKey = `${data.kind}|${data.value}`;
      const ioc = state.activeIOCs.get(extendKey);
      if (ioc) {
        ioc.endTime += 60_000;
        state.activeIOCs.set(extendKey, ioc);
        log.info("IOC extended", { ioc_key: extendKey });
      }
      break;
    }

    case "quarantineIOC": {
      const quarantineKey = `${data.kind}|${data.value}`;
      const ioc = state.activeIOCs.get(quarantineKey);
      if (ioc) {
        ioc.endTime = Date.now() + 24 * 60 * 60 * 1000;
        state.activeIOCs.set(quarantineKey, ioc);
        log.info("IOC quarantined", { ioc_key: quarantineKey });
      }
      break;
    }

    case "simulateFalsePositive": {
      const fpNodeId = data.nodeId || "node-1";
      const fpIOC = {
        kind: "ip" as const,
        value: "192.168.1.100",
        reason: "Simulation FP",
        source: fpNodeId,
        confidence: 95,
        ttl_sec: 60,
        firstSeen: Date.now(),
      };
      if (publish(deps, "ioc.local", fpIOC)) {
        log.info("FP simulation triggered", { node_id: fpNodeId });
      } else {
        log.warn("NATS connection not available for simulation");
      }
      break;
    }

    case "simulateIOCFlood": {
      const floodNodeId = "node-flood";
      for (let i = 0; i < 15; i++) {
        const floodIOC = {
          kind: "ip" as const,
          value: `192.168.1.${100 + i}`,
          reason: "Simulation Flood",
          source: floodNodeId,
          confidence: 80,
          ttl_sec: 30,
          firstSeen: Date.now(),
        };
        publish(deps, "ioc.local", floodIOC);
      }
      log.info("IOC flood simulation triggered");
      break;
    }

    case "simulateNodeIsolation": {
      const isolatedNodeId = "node-isolated";
      const node = state.nodes.get(isolatedNodeId) || defaultNode(isolatedNodeId, Date.now());
      // On force lastHeartbeat à 20s dans le passé pour que la prochaine
      // boucle SLO marque le node comme isolé.
      node.lastHeartbeat = Date.now() - 20_000;
      node.health = "isolated";
      state.nodes.set(isolatedNodeId, node);
      broadcast({ type: "event", payload: { kind: "node_isolated", nodeId: isolatedNodeId, reason: "Simulation isolation" } });
      log.info("node isolation simulation triggered", { node_id: isolatedNodeId });
      break;
    }

    case "toggleFailMode":
      state.failMode = state.failMode === "fail-open" ? "fail-closed" : "fail-open";
      log.info("fail-mode changed", { fail_mode: state.failMode });
      break;

    case "trafficControl":
      handleTrafficControl(deps, data.command, data.targetNodeId);
      break;

    case "runGuidedDemo":
      runGuidedDemo(deps);
      break;
  }
}

export function attachWSCommands(wss: WebSocketServer, deps: CommandsDeps): void {
  wss.on("connection", (ws) => {
    log.info("websocket client connected");

    ws.on("message", (message) => {
      try {
        handleWSMessage(deps, message.toString());
      } catch (e) {
        log.error("error parsing WS command", { err: e });
      }
    });

    ws.on("close", () => {
      log.info("websocket client disconnected");
    });
  });
}
