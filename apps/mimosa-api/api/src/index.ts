import express from 'express';
import http from 'http';
import cors from 'cors';
import { Server as IOServer } from 'socket.io';
import { MimosaLimiter, LimiterConfig } from './limiter.js';
import type { TripEvent } from './types.js';


const PORT = Number(process.env.PORT ?? 8080);


const app = express();
app.use(cors());
app.use(express.json());


const server = http.createServer(app);
const io = new IOServer(server, { cors: { origin: '*' } });


// util pour IP (derrière proxy Docker, Express peut retourner l'IP interne)
app.set('trust proxy', false);
const getIp = (req: express.Request) => {
    // Priorité au header X-Fake-IP pour la simulation
    const fakeIp = req.headers['x-fake-ip'] as string;
    if (fakeIp) {
        console.log(`🎭 Simulation IP: ${fakeIp} (IP réelle: ${req.ip || req.socket.remoteAddress || 'unknown'})`);
        return fakeIp;
    }
    return req.ip || req.socket.remoteAddress || 'unknown';
};


// Configuration du limiteur
const config: LimiterConfig = {
    windowS: parseInt(process.env.WINDOW_S || '10'),
    thresholdRps: parseInt(process.env.THRESHOLD_RPS || '10'),
    pathDiversity: parseInt(process.env.PATH_DIVERSITY || '5'),
    tripMs: parseInt(process.env.TRIP_MS || '30000')
};

const limiter = new MimosaLimiter(config);

// Callbacks pour les événements
limiter.onTrip = (ev: TripEvent) => {
    console.log(`🚨 IP ${ev.ip} tripped: ${ev.reason} (TTL: ${ev.ttlMs}ms)`);
    io.emit('trip', ev); // push au dashboard
};

limiter.onRecover = (ip: string) => {
    console.log(`✅ IP ${ip} recovered`);
    io.emit('recover', { ip });
};


// Middleware Mimosa: mesure, décide, éventuellement bloque
app.use((req, res, next) => {
    const ip = getIp(req);
    const path = req.path;

    // Exclure la route racine, les fichiers statiques et l'API state du filtrage
    if (path === '/' || path.startsWith('/public/') || path.startsWith('/static/') || path === '/api/state') {
        console.log(`📁 Route statique exclue du filtrage: ${path}`);
        return next();
    }

    // Vérifier d'abord si l'IP est déjà repliée
    const { tripped, ttlMs } = limiter.isTripped(ip);
    if (tripped) {
        return res.status(429).json({
            status: 'folded',
            ip,
            ttlMs,
            message: 'Mimosa plié — calme requis avant réouverture.'
        });
    }

    // Enregistrer la requête et vérifier si elle déclenche un repli
    const rec = limiter.record(ip, path);
    
    // Vérifier à nouveau après l'enregistrement
    const { tripped: nowTripped, ttlMs: newTtlMs } = limiter.isTripped(ip);
    if (nowTripped) {
        // L'événement 'trip' est déjà émis par limiter.onTrip, pas besoin de le refaire ici
        return res.status(429).json({
            status: 'folded',
            ip,
            ttlMs: newTtlMs,
            message: 'Mimosa plié — calme requis avant réouverture.'
        });
    }

    // Émettre les métriques pour les requêtes normales
    console.log(`📡 Émission WebSocket metrics: IP=${ip}, RPS=${rec.rps}`);
    io.emit('metrics', { ip, rps: rec.rps });
    next();
});


// Endpoint "normal"
app.get('/api/data', (_req, res) => {
    // charge simulée minime
    const payload = { time: new Date().toISOString(), value: Math.random() };
    res.json(payload);
});


// Endpoint "bruyant" pour tester la diversité de chemins
app.get('/api/noisy/:id', (req, res) => {
    res.json({ ok: true, id: req.params.id, time: Date.now() });
});


// État pour le dashboard
app.get('/api/state', (_req, res) => {
    res.json({ items: limiter.snapshot(50) });
});

// Configuration pour le dashboard
app.get('/api/config', (_req, res) => {
    res.json({
        thresholdRps: config.thresholdRps,
        pathDiversity: config.pathDiversity,
        tripMs: config.tripMs,
        windowS: config.windowS
    });
});


// Route pour la page d'accueil
app.get('/', (req, res) => {
    res.sendFile('index.html', { root: './src' });
});

// Fichiers statiques (dashboard)
app.use('/', express.static('public'));


server.listen(PORT, () => {
    console.log(`Mimosa API en écoute sur http://localhost:${PORT}`);
});