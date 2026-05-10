// node/src/index.ts
import os from 'os';
import { connect, StringCodec, NatsConnection, Subscription } from "nats";
import { log } from "./log";

type TrafficEvent = {
  nodeId: string;
  ts?: number;
  src_ip: string;
  path?: string;
  status?: number;
  ua?: string;
};

type AlertMsg = {
  nodeId: string;
  rule: string;
  src_ip: string;
  count_5s: number;
  confidence: number;
  ts: number;
};

type IOCMsg = {
  kind: "ip" | "ua" | "path";
  value: string;
  reason: string;
  source: string;
  confidence: number;
  ttl_sec: number;
  firstSeen: number;
};

type DropMsg = {
  nodeId: string;
  ts: number;
  ip: string;
  reason: string;
};

const NODE_ID = process.env.NODE_ID || os.hostname();
const NATS_URL = process.env.NATS_URL || "nats://bus:4222";

const WINDOW_MS = parseInt(process.env.WINDOW_MS || "5000", 10);
const THRESH = parseInt(process.env.THRESH || "20", 10);
const BLOCK_TTL_SEC = parseInt(process.env.BLOCK_TTL_SEC || "180", 10);

const BAD_PATHS = new Set(
  (process.env.BAD_PATHS || "/wp-login.php,/xmlrpc.php,/admin/login")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean)
);
const BAD_STATUS = new Set(
  (process.env.BAD_STATUS || "401,403")
    .split(",")
    .map((s) => parseInt(s.trim(), 10))
    .filter((n) => !Number.isNaN(n))
);

const sc = StringCodec();
const byIP: Map<string, number[]> = new Map();
const blocklistIP: Map<string, number> = new Map();

const now = () => Date.now();

function withinWindow(ts: number) { return ts >= now() - WINDOW_MS; }

function purgeOld(ip: string): number {
  const arr = byIP.get(ip) || [];
  const kept = arr.filter(withinWindow);
  byIP.set(ip, kept);
  return kept.length;
}

function clamp01(x: number) { return x < 0 ? 0 : x > 1 ? 1 : x; }

function computeConfidence(count: number, badPathFlag: number, badStatusFlag: number) {
  const conf = 0.5 + 0.02 * count + 0.2 * badPathFlag + 0.2 * badStatusFlag;
  return clamp01(conf);
}

function isBlocked(ip: string) {
  const exp = blocklistIP.get(ip);
  if (!exp) return false;
  if (exp <= now()) { blocklistIP.delete(ip); return false; }
  return true;
}

function applyIPBlock(ip: string, ttlSec: number) {
  blocklistIP.set(ip, now() + ttlSec * 1000);
}

setInterval(() => {
  const t = now();
  for (const [ip, exp] of blocklistIP.entries()) {
    if (exp <= t) blocklistIP.delete(ip);
  }
}, 1000);

function pubAlert(nc: NatsConnection, alert: AlertMsg) {
  nc.publish(`alerts.${NODE_ID}`, sc.encode(JSON.stringify(alert)));
}

function pubIOCLocal(nc: NatsConnection, ioc: IOCMsg) {
  nc.publish("ioc.local", sc.encode(JSON.stringify(ioc)));
}

function pubDrop(nc: NatsConnection, drop: DropMsg) {
  nc.publish(`drops.${NODE_ID}`, sc.encode(JSON.stringify(drop)));
}

async function main() {
  const nc = await connect({ servers: NATS_URL });
  log.info("connected to NATS", { node_id: NODE_ID, url: NATS_URL });

  // Presence: annonce périodique du nodeId
  setInterval(() => {
    nc.publish("nodes.hello", sc.encode(JSON.stringify({ nodeId: NODE_ID, ts: now() })));
  }, 5000);

  // Fonction pour appliquer un IOC et envoyer l'ACK
  function applyIOCAndAck(ioc: IOCMsg, source: string) {
    if (ioc.kind === "ip") {
      // Calculer le TTL restant si l'IOC a un endTime
      const ttlSec = ioc.ttl_sec || BLOCK_TTL_SEC;
      applyIPBlock(ioc.value, ttlSec);
      log.info("applied shared IOC", { node_id: NODE_ID, ip: ioc.value, ttl_sec: ttlSec, source });
      
      // Envoyer un accusé de réception pour indiquer que l'IOC a été appliqué
      const iocKey = `${ioc.kind}|${ioc.value}`;
      nc.publish(`ack.${NODE_ID}`, sc.encode(JSON.stringify({
        nodeId: NODE_ID,
        iocKey: iocKey,
        ts: now()
      })));
    }
  }

  // Synchronisation des IOC actifs au démarrage
  async function syncActiveIOCs() {
    try {
      log.info("requesting active IOCs sync", { node_id: NODE_ID });
      const reply = await nc.request("ioc.sync.request", sc.encode(JSON.stringify({ nodeId: NODE_ID })), { timeout: 5000 });
      const response = JSON.parse(sc.decode(reply.data));
      
      if (response.iocs && Array.isArray(response.iocs)) {
        log.info("received active IOCs from controller", { node_id: NODE_ID, count: response.iocs.length });
        for (const iocData of response.iocs) {
          const ioc: IOCMsg = {
            kind: iocData.kind,
            value: iocData.value,
            reason: iocData.reason || "synced",
            source: iocData.source || "controller",
            confidence: iocData.confidence || 0.8,
            ttl_sec: iocData.ttl_sec || BLOCK_TTL_SEC,
            firstSeen: iocData.firstSeen || now()
          };
          applyIOCAndAck(ioc, "controller-sync");
        }
      }
    } catch (e) {
      log.error("error syncing active IOCs", { node_id: NODE_ID, err: e });
      // Continue even if sync fails - node will still receive future IOC shares
    }
  }

  // Synchroniser les IOC actifs au démarrage (après un court délai pour s'assurer que NATS est prêt)
  setTimeout(() => {
    syncActiveIOCs();
  }, 2000);

  const subIOCShare: Subscription = nc.subscribe("ioc.share", {
    callback: (_err, m) => {
      try {
        const ioc = JSON.parse(sc.decode(m.data)) as IOCMsg;
        applyIOCAndAck(ioc, ioc.source);
      } catch (e) {
        log.error("error parsing ioc.share", { node_id: NODE_ID, err: e });
      }
    },
  });

  const subTraffic: Subscription = nc.subscribe("traffic.http", {
    callback: (_err, m) => {
      try {
        const ev = JSON.parse(sc.decode(m.data)) as TrafficEvent;
        if (ev.nodeId !== NODE_ID) return; // ne traiter que mon trafic

        const eventTs = ev.ts || now();

        if (isBlocked(ev.src_ip)) {
          pubDrop(nc, { nodeId: NODE_ID, ts: eventTs, ip: ev.src_ip, reason: "ioc-block" });
          return;
        }

        const arr = byIP.get(ev.src_ip) || [];
        arr.push(eventTs);
        byIP.set(ev.src_ip, arr);
        const count = purgeOld(ev.src_ip);

        const badPathFlag = ev.path && BAD_PATHS.has(ev.path) ? 1 : 0;
        const badStatusFlag = ev.status && BAD_STATUS.has(ev.status) ? 1 : 0;

        if (count >= THRESH && (badPathFlag || badStatusFlag)) {
          const confidence = computeConfidence(count, badPathFlag, badStatusFlag);

          const alert: AlertMsg = {
            nodeId: NODE_ID,
            rule: "login-burst",
            src_ip: ev.src_ip,
            count_5s: count,
            confidence,
            ts: now(),
          };
          pubAlert(nc, alert);

          const ioc: IOCMsg = {
            kind: "ip",
            value: ev.src_ip,
            reason: "login-burst",
            source: NODE_ID,
            confidence,
            ttl_sec: BLOCK_TTL_SEC,
            firstSeen: arr.length ? arr[0] : eventTs,
          };
          pubIOCLocal(nc, ioc);
          applyIPBlock(ioc.value, ioc.ttl_sec);

          log.warn("ALERT - local IOC raised", { node_id: NODE_ID, ip: ev.src_ip, count_5s: count, confidence });
        }
      } catch (e) {
        log.error("error parsing traffic.http", { node_id: NODE_ID, err: e });
      }
    },
  });

  const shutdown = async () => {
    log.info("shutting down", { node_id: NODE_ID });
    try {
      subTraffic.unsubscribe();
      subIOCShare.unsubscribe();
      await nc.drain();
    } catch (e) {
      log.error("error on shutdown", { node_id: NODE_ID, err: e });
    } finally {
      process.exit(0);
    }
  };
  process.on("SIGTERM", shutdown);
  process.on("SIGINT", shutdown);

  log.info("node ready", { node_id: NODE_ID, window_ms: WINDOW_MS, thresh: THRESH, block_ttl_sec: BLOCK_TTL_SEC });
}

main().catch((e) => {
  log.error("fatal error", { node_id: NODE_ID, err: e });
  process.exit(1);
});
