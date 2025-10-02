// traffic/src/index.ts
import { connect, StringCodec } from "nats";

const NATS_URL = process.env.NATS_URL || "nats://bus:4222";
const sc = StringCodec();

function now(){ return Date.now(); }

// Liste de nodes actifs (vue par 'hello')
const nodes = new Map<string, number>(); // nodeId -> lastSeen ms

function pickNode(): string {
  const active = Array.from(nodes.entries())
    .filter(([_,ts]) => (now() - ts) < 20000)
    .map(([id,_]) => id);
  if (active.length === 0) return "node-1";
  return active[Math.floor(Math.random() * active.length)];
}

function randomIP(){
  return `198.51.100.${Math.floor(Math.random()*200)+1}`;
}

(async()=>{
  const nc = await connect({ servers: NATS_URL });
  console.log("[traffic] connected");

  // écoute des nodes "hello"
  nc.subscribe("nodes.hello", {
    callback: (_e,m)=>{
      try {
        const hello = JSON.parse(sc.decode(m.data));
        if (hello.nodeId) nodes.set(hello.nodeId, hello.ts || now());
      } catch {}
    }
  });

  // bruit "normal"
  setInterval(()=>{
    const nodeId = pickNode();
    const ev = {
      nodeId,
      ts: now(),
      src_ip: randomIP(),
      path: "/",
      status: 200,
      ua: "Mozilla"
    };
    nc.publish("traffic.http", sc.encode(JSON.stringify(ev)));
  }, 60);

  // burst toutes les 20s sur un node aléatoire
  setInterval(async ()=>{
    const target = pickNode();
    const badIP = "203.0.113.66";
    for (let i=0;i<30;i++){
      const ev = {
        nodeId: target,
        ts: now(),
        src_ip: badIP,
        path: "/wp-login.php",
        status: 401,
        ua: "B4dB0t"
      };
      nc.publish("traffic.http", sc.encode(JSON.stringify(ev)));
      await new Promise(r=>setTimeout(r, 50));
    }
    console.log(`[traffic] burst sent to ${target}`);
  }, 20000);
})().catch((e)=>{
  console.error("[traffic] fatal error", e);
  process.exit(1);
});
