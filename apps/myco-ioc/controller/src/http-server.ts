// HTTP server (statique + /api/state) et broadcaster WebSocket.
// Le broadcaster est créé en deux temps : la fonction broadcast est
// retournée immédiatement (capturée par les autres modules) mais ne
// fait rien tant qu'on n'a pas appelé attachWss(wss).

import * as http from "http";
import * as fs from "fs";
import * as path from "path";
import WebSocket, { WebSocketServer } from "ws";
import { AppState } from "./state";

export type Broadcaster = {
  broadcast: (msg: any) => void;
  attachWss: (wss: WebSocketServer) => void;
};

export function createBroadcaster(): Broadcaster {
  let wss: WebSocketServer | null = null;
  return {
    broadcast(msg: any): void {
      if (!wss) return;
      const json = JSON.stringify(msg);
      for (const client of wss.clients) {
        if (client.readyState === WebSocket.OPEN) client.send(json);
      }
    },
    attachWss(s: WebSocketServer): void {
      wss = s;
    },
  };
}

export type HttpServerOpts = {
  state: AppState;
  version: string;
  buildTimestamp: string;
  publicDir?: string;
};

function serveStatic(filePath: string, res: http.ServerResponse, contentType = "text/html"): void {
  fs.readFile(filePath, (err, data) => {
    if (err) {
      res.writeHead(404);
      res.end("File not found");
      return;
    }
    // charset explicite : les libellés de l'UI sont en français accentué.
    res.writeHead(200, { "Content-Type": `${contentType}; charset=utf-8` });
    res.end(data);
  });
}

export function createHttpServer(opts: HttpServerOpts): http.Server {
  const publicDir = opts.publicDir || path.join(__dirname, "..", "public");

  return http.createServer((req, res) => {
    const url = req.url || "/";

    if (url === "/" || url === "/index.html") {
      serveStatic(path.join(publicDir, "index.html"), res);
      return;
    }
    if (url === "/visual" || url === "/visual.html") {
      serveStatic(path.join(publicDir, "visual.html"), res);
      return;
    }
    // Assets partagés par les deux dashboards.
    // path.basename coupe toute tentative de traversée ("../../etc/passwd").
    const asset = /^\/shared\/([A-Za-z0-9._-]+\.(css|js))$/.exec(url.split("?")[0]);
    if (asset) {
      const contentType = asset[2] === "css" ? "text/css" : "text/javascript";
      serveStatic(path.join(publicDir, "shared", path.basename(asset[1])), res, contentType);
      return;
    }
    if (url === "/api/state") {
      const snapshot = {
        version: opts.version,
        nodes: Array.from(opts.state.nodes.values()).map(n => ({
          id: n.id,
          health: n.health,
          alerts: n.alerts,
          drops: n.drops,
          lastSeen: n.lastSeen,
        })),
        activeIOCs: Array.from(opts.state.activeIOCs.values()).map(ioc => ({
          kind: ioc.kind,
          value: ioc.value,
          source: ioc.source,
          confidence: ioc.confidence,
          ttl_sec: ioc.ttl_sec,
          startTime: ioc.startTime,
          endTime: ioc.endTime,
        })),
        globalQuorum: opts.state.globalQuorum,
        floodMode: opts.state.floodMode,
        timestamp: Date.now(),
      };
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(snapshot));
      return;
    }

    res.writeHead(404);
    res.end("Not found");
  });
}
