// controller/src/index.ts
import { connect, StringCodec } from "nats";
import WebSocket, { WebSocketServer } from "ws";

const NATS_URL = process.env.NATS_URL || "nats://bus:4222";
const QUORUM = parseInt(process.env.QUORUM || "1", 10);
const DEFAULT_TTL = parseInt(process.env.DEFAULT_TTL_SEC || "180", 10);
const WS_PORT = parseInt(process.env.WS_PORT || "8080", 10);

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

type NodeState = { id: string; alerts: number; drops: number; health: "ok"|"under-attack" };

const state = {
  nodes: new Map<string, NodeState>(),
  events: [] as any[]
};

const votes = new Map<string, Set<string>>(); // key=kind|value -> sources
function keyOf(ioc: IOC){ return `${ioc.kind}|${ioc.value}`; }

const wss = new WebSocketServer({ port: WS_PORT });
function broadcast(obj:any){
  const msg = JSON.stringify(obj);
  for (const client of wss.clients) if (client.readyState === WebSocket.OPEN) client.send(msg);
}
setInterval(() => {
  broadcast({type:"state", payload:{ nodes: Array.from(state.nodes.values()) }});
}, 500);

(async()=>{
  const nc = await connect({ servers: NATS_URL });
  console.log(`[controller] connected to ${NATS_URL}, WS on ${WS_PORT}`);

  // Découverte des nodes
  await nc.subscribe("nodes.hello", {
    callback: (_e,m) => {
      try {
        const hello = JSON.parse(sc.decode(m.data));
        const id = hello.nodeId || "unknown";
        const n = state.nodes.get(id) || { id, alerts: 0, drops: 0, health: "ok" as const };
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
      const n = state.nodes.get(id) || {id, alerts:0, drops:0, health:"ok"};
      n.alerts++; n.health = "under-attack";
      state.nodes.set(id, n);
      broadcast({type:"event", payload:{kind:"alert", ...alert}});
    }
  });

  // Drops
  await nc.subscribe("drops.*", {
    callback: (_e,m)=>{
      const drop = JSON.parse(sc.decode(m.data));
      const id = drop.nodeId;
      const n = state.nodes.get(id) || {id, alerts:0, drops:0, health:"ok"};
      n.drops++; state.nodes.set(id, n);
      broadcast({type:"event", payload:{kind:"drop", ...drop}});
    }
  });

  // IOC quorum -> share
  await nc.subscribe("ioc.local", {
    callback: (_e, m)=>{
      const ioc = JSON.parse(sc.decode(m.data)) as IOC;
      const k = keyOf(ioc);
      if (!votes.has(k)) votes.set(k, new Set());
      votes.get(k)!.add(ioc.source);
      broadcast({type:"event", payload:{...ioc, eventType:"ioc.local"}});

      if (votes.get(k)!.size >= QUORUM){
        const shared = { ...ioc, ttl_sec: DEFAULT_TTL };
        nc.publish("ioc.share", sc.encode(JSON.stringify(shared)));
        broadcast({type:"event", payload:{...shared, eventType:"ioc.share"}});
      }
    }
  });
})().catch((e)=>{
  console.error("[controller] fatal error", e);
  process.exit(1);
});
