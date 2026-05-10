// Smoke test : valide le flux nominal détection -> ioc.share sur la stack
// docker-compose courante. À lancer depuis WSL ou un host qui voit
// nats://localhost:4222 et http://localhost:3000.
//
// Variables d'env :
//   NATS_URL        défaut nats://localhost:4222
//   CONTROLLER_URL  défaut http://localhost:3000
//
// Usage :
//   cd apps/myco-ioc/tests
//   npm install
//   npm test

import { test } from "node:test";
import assert from "node:assert/strict";
import { connect, StringCodec } from "nats";

const NATS_URL = process.env.NATS_URL || "nats://localhost:4222";
const CONTROLLER_URL = process.env.CONTROLLER_URL || "http://localhost:3000";
const ATTACK_IP = "203.0.113.66";
const BURST_SIZE = 25;
const HELLO_TIMEOUT_MS = 10_000;
const IOC_TIMEOUT_MS = 5_000;

const sc = StringCodec();

async function getState() {
  const r = await fetch(`${CONTROLLER_URL}/api/state`);
  if (!r.ok) throw new Error(`controller /api/state HTTP ${r.status}`);
  return r.json();
}

async function waitForCondition(check, timeoutMs, intervalMs = 200, label = "condition") {
  const deadline = Date.now() + timeoutMs;
  let lastErr;
  while (Date.now() < deadline) {
    try {
      const ok = await check();
      if (ok) return ok;
    } catch (e) { lastErr = e; }
    await new Promise(r => setTimeout(r, intervalMs));
  }
  throw new Error(`timeout waiting for ${label} (${timeoutMs}ms)${lastErr ? `: ${lastErr.message}` : ""}`);
}

test("controller is reachable and stack has at least one node", async () => {
  const s = await getState();
  assert.ok(s.version, "controller returns a version");
  // Si la stack vient d'être lancée, les nodes peuvent ne pas encore avoir
  // émis nodes.hello. On laisse jusqu'à HELLO_TIMEOUT_MS.
  await waitForCondition(
    async () => (await getState()).nodes.length > 0,
    HELLO_TIMEOUT_MS,
    500,
    "at least one node registered"
  );
});

test("burst on a node triggers an active shared IOC within 5s", async () => {
  const s0 = await getState();
  const target = s0.nodes[0]?.id;
  assert.ok(target, "a target node must be registered");

  const nc = await connect({ servers: NATS_URL });
  try {
    // Publie BURST_SIZE events qui matchent la règle login-burst
    // (path malicieux + status 401 + > THRESH événements en WINDOW_MS)
    const ts = Date.now();
    for (let i = 0; i < BURST_SIZE; i++) {
      const ev = {
        nodeId: target,
        ts,
        src_ip: ATTACK_IP,
        path: "/wp-login.php",
        status: 401
      };
      nc.publish("traffic.http", sc.encode(JSON.stringify(ev)));
    }
    await nc.flush();

    // Le controller doit voter et passer en activeIOCs sous 5s
    const found = await waitForCondition(
      async () => {
        const s = await getState();
        return s.activeIOCs.find(ioc => ioc.value === ATTACK_IP);
      },
      IOC_TIMEOUT_MS,
      200,
      `active IOC for ${ATTACK_IP}`
    );

    assert.equal(found.kind, "ip");
    assert.equal(found.value, ATTACK_IP);
    assert.ok(found.endTime > Date.now(), "IOC should have a future TTL");
  } finally {
    await nc.drain();
  }
});
