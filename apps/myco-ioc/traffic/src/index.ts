// traffic/src/index.ts
import { connect, StringCodec } from "nats";

const NATS_URL = process.env.NATS_URL || "nats://bus:4222";
const sc = StringCodec();

function now(){ return Date.now(); }

// Liste de nodes actifs (vue par 'hello')
const nodes = new Map<string, number>(); // nodeId -> lastSeen ms

// État du trafic
let trafficState = 'stop'; // 'stop', 'low', 'normal' - démarrer arrêté par défaut
let normalInterval: NodeJS.Timeout | null = null;
let burstInterval: NodeJS.Timeout | null = null;

// Variable globale pour la connexion NATS
let nc: any = null;

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

function startNormalTraffic(interval: number = 60) {
  if (normalInterval) clearInterval(normalInterval);
  
  normalInterval = setInterval(() => {
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
  }, interval);
  
  console.log(`[traffic] Normal traffic started (interval: ${interval}ms)`);
}

function startBurstTraffic() {
  if (burstInterval) clearInterval(burstInterval);
  
  burstInterval = setInterval(async () => {
    const target = pickNode();
    const badIP = "203.0.113.66";
    for (let i = 0; i < 30; i++) {
      const ev = {
        nodeId: target,
        ts: now(),
        src_ip: badIP,
        path: "/wp-login.php",
        status: 401,
        ua: "B4dB0t"
      };
      nc.publish("traffic.http", sc.encode(JSON.stringify(ev)));
      await new Promise(r => setTimeout(r, 50));
    }
    console.log(`[traffic] burst sent to ${target}`);
  }, 20000);
  
  console.log(`[traffic] Burst traffic started`);
}

function stopAllTraffic() {
  if (normalInterval) {
    clearInterval(normalInterval);
    normalInterval = null;
  }
  if (burstInterval) {
    clearInterval(burstInterval);
    burstInterval = null;
  }
  console.log(`[traffic] All traffic stopped`);
}

(async()=>{
  nc = await connect({ servers: NATS_URL });
  console.log("[traffic] connected");

  // écoute des nodes "hello"
  nc.subscribe("nodes.hello", {
    callback: (_e: any, m: any)=>{
      try {
        const hello = JSON.parse(sc.decode(m.data));
        if (hello.nodeId) nodes.set(hello.nodeId, hello.ts || now());
      } catch {}
    }
  });

  // écoute des commandes de contrôle de trafic
  nc.subscribe("traffic.control", {
    callback: (_e: any, m: any)=>{
      try {
        const control = JSON.parse(sc.decode(m.data));
        console.log(`[traffic] Control command received: ${control.action}`);
        
        switch (control.action) {
          case 'stop':
            stopAllTraffic();
            trafficState = 'stop';
            break;
          case 'low':
            stopAllTraffic();
            startNormalTraffic(control.interval || 2000);
            trafficState = 'low';
            break;
          case 'normal':
            stopAllTraffic();
            startNormalTraffic(control.interval || 60);
            startBurstTraffic();
            trafficState = 'normal';
            break;
        }
      } catch (e) {
        console.error(`[traffic] Error parsing control command:`, e);
      }
    }
  });

  // Démarrer arrêté par défaut - l'utilisateur peut démarrer le trafic via l'interface
  console.log("[traffic] Service démarré - Trafic arrêté par défaut");
  console.log("[traffic] Utilisez l'interface pour démarrer le trafic souhaité");
})().catch((e)=>{
  console.error("[traffic] fatal error", e);
  process.exit(1);
});
