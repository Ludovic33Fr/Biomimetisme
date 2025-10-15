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


// Configuration par défaut (10 RPS comme demandé)
const defaultConfig: LimiterConfig = {
    windowS: parseInt(process.env.WINDOW_S || '10'),
    thresholdRps: parseInt(process.env.THRESHOLD_RPS || '10'), // 10 RPS par défaut
    pathDiversity: parseInt(process.env.PATH_DIVERSITY || '5'),
    tripMs: parseInt(process.env.TRIP_MS || '30000')
};

// Configuration spécifique pour les réservations PS5 (0.5 RPS comme demandé)
const reservationConfig: LimiterConfig = {
    windowS: 5, // Fenêtre plus courte
    thresholdRps: 0.3, // Seuil RPS très bas (0.3 RPS)
    pathDiversity: 2, // Diversité plus stricte
    tripMs: 60000 // Blocage plus long (1 minute)
};

// Système de configuration par route
interface RouteConfig {
    path: string;
    config: LimiterConfig;
    limiter: MimosaLimiter;
}

const routeConfigs: RouteConfig[] = [
    {
        path: '/api/reserve-ps5',
        config: reservationConfig,
        limiter: new MimosaLimiter(reservationConfig)
    }
];

// Limiteur par défaut
const defaultLimiter = new MimosaLimiter(defaultConfig);

// Fonction pour obtenir le limiteur approprié pour une route
function getLimiterForRoute(path: string): MimosaLimiter {
    const routeConfig = routeConfigs.find(rc => path === rc.path);
    return routeConfig ? routeConfig.limiter : defaultLimiter;
}

// Configuration des callbacks pour tous les limiteurs
function setupLimiterCallbacks(limiter: MimosaLimiter, routePath: string = 'default') {
    limiter.onTrip = (ev: TripEvent) => {
        const prefix = routePath === '/api/reserve-ps5' ? '🎮 RÉSERVATION BLOQUÉE' : '🚨 IP';
        console.log(`${prefix} ${ev.ip} tripped: ${ev.reason} (TTL: ${ev.ttlMs}ms)`);
        io.emit('trip', ev); // push au dashboard
    };

    limiter.onRecover = (ip: string) => {
        const prefix = routePath === '/api/reserve-ps5' ? '🎮 RÉSERVATION DÉBLOQUÉE' : '✅ IP';
        console.log(`${prefix} ${ip} recovered`);
        io.emit('recover', { ip });
    };
}

// Configurer les callbacks pour tous les limiteurs
setupLimiterCallbacks(defaultLimiter, 'default');
routeConfigs.forEach(routeConfig => {
    setupLimiterCallbacks(routeConfig.limiter, routeConfig.path);
});


// Middleware Mimosa: mesure, décide, éventuellement bloque
app.use((req, res, next) => {
    const ip = getIp(req);
    const path = req.path;

    // Exclure la route racine, les fichiers statiques et l'API state du filtrage
    if (path === '/' || path.startsWith('/public/') || path.startsWith('/static/') || path === '/api/state') {
        console.log(`📁 Route statique exclue du filtrage: ${path}`);
        return next();
    }

    // Obtenir le limiteur approprié pour cette route
    const limiter = getLimiterForRoute(path);
    const isReservationRoute = path === '/api/reserve-ps5';

    // Vérifier d'abord si l'IP est déjà repliée
    const { tripped, ttlMs } = limiter.isTripped(ip);
    if (tripped) {
        const message = isReservationRoute 
            ? 'Trop de tentatives de réservation. Veuillez patienter.'
            : 'Mimosa plié — calme requis avant réouverture.';
        return res.status(429).json({
            status: 'folded',
            ip,
            ttlMs,
            message
        });
    }

    // Enregistrer la requête et vérifier si elle déclenche un repli
    const rec = limiter.record(ip, path);
    
    // Vérifier à nouveau après l'enregistrement
    const { tripped: nowTripped, ttlMs: newTtlMs } = limiter.isTripped(ip);
    if (nowTripped) {
        // L'événement 'trip' est déjà émis par limiter.onTrip, pas besoin de le refaire ici
        const message = isReservationRoute 
            ? 'Trop de tentatives de réservation. Veuillez patienter.'
            : 'Mimosa plié — calme requis avant réouverture.';
        return res.status(429).json({
            status: 'folded',
            ip,
            ttlMs: newTtlMs,
            message
        });
    }

    // Émettre les métriques pour les requêtes normales
    console.log(`📡 Émission WebSocket metrics: IP=${ip}, RPS=${rec.rps}, Route=${path}`);
    io.emit('metrics', { ip, rps: rec.rps, route: path });
    next();
});


// Endpoint pour effacer les données IP
app.post('/api/clear-ips', (req, res) => {
    console.log('🗑️ Demande d\'effacement des données IP');
    
    // Effacer les données du limiteur par défaut
    defaultLimiter.clear();
    
    // Effacer les données de tous les limiteurs de routes spécifiques
    routeConfigs.forEach(routeConfig => {
        routeConfig.limiter.clear();
    });
    
    // Notifier tous les clients connectés
    io.emit('ips-cleared', { message: 'Données IP effacées' });
    
    res.json({ 
        status: 'success', 
        message: 'Données IP effacées avec succès',
        timestamp: new Date().toISOString()
    });
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
    res.json({ items: defaultLimiter.snapshot(50) });
});

// Configuration pour le dashboard
app.get('/api/config', (_req, res) => {
    res.json({
        default: {
            thresholdRps: defaultConfig.thresholdRps,
            pathDiversity: defaultConfig.pathDiversity,
            tripMs: defaultConfig.tripMs,
            windowS: defaultConfig.windowS
        },
        routes: routeConfigs.map(rc => ({
            path: rc.path,
            config: rc.config
        }))
    });
});


// Route pour la page d'accueil
app.get('/', (req, res) => {
    res.sendFile('index.html', { root: './public' });
});

// Route pour la page de réservation PlayStation 5
app.get('/ps5', (req, res) => {
    res.sendFile('ps5-reservation.html', { root: './public' });
});

// Endpoint de réservation PlayStation 5 (rate limiting géré par le middleware)
app.post('/api/reserve-ps5', (req, res) => {
    const ip = getIp(req);
    const { email, name, phone, quantity } = req.body;
    
    // Validation des données
    if (!email || !name || !phone || !quantity) {
        return res.status(400).json({
            success: false,
            message: 'Tous les champs sont requis'
        });
    }
    
    // Simulation de la réservation
    const reference = `PS5-${Date.now()}-${Math.random().toString(36).substr(2, 6).toUpperCase()}`;
    
    // Log de la réservation
    console.log(`🎮 Réservation PlayStation 5: ${email} - Référence: ${reference}`);
    
    res.json({
        success: true,
        message: 'Réservation confirmée',
        reference,
        details: {
            email,
            name,
            phone,
            quantity: parseInt(quantity),
            price: 499.99 * parseInt(quantity),
            estimatedDelivery: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
        }
    });
});

// Fichiers statiques (dashboard)
app.use('/', express.static('public'));

// Gestion des connexions Socket.IO pour le dashboard
io.on('connection', (socket) => {
    console.log('📱 Client dashboard connecté:', socket.id);
    
    // Envoyer les métriques actuelles
    const tripStatus = defaultLimiter.isTripped('dashboard');
    socket.emit('metrics', {
        rps: defaultLimiter.getCurrentRps(),
        diversity: defaultLimiter.getCurrentDiversity(),
        tripped: tripStatus.tripped,
        ttl: tripStatus.ttlMs
    });
    
    // Gestion des demandes de métriques
    socket.on('get_metrics', () => {
        const tripStatus = defaultLimiter.isTripped('dashboard');
        socket.emit('metrics', {
            rps: defaultLimiter.getCurrentRps(),
            diversity: defaultLimiter.getCurrentDiversity(),
            tripped: tripStatus.tripped,
            ttl: tripStatus.ttlMs
        });
    });
    
    socket.on('disconnect', () => {
        console.log('📱 Client dashboard déconnecté:', socket.id);
    });
});

// Fonction pour diffuser les métriques à tous les clients connectés
function broadcastMetrics() {
    const tripStatus = defaultLimiter.isTripped('dashboard');
    const metrics = {
        rps: defaultLimiter.getCurrentRps(),
        diversity: defaultLimiter.getCurrentDiversity(),
        tripped: tripStatus.tripped,
        ttl: tripStatus.ttlMs
    };
    
    // Récupérer les données des IPs
    const ipData = defaultLimiter.snapshot(20);
    
    console.log('📊 Diffusion métriques:', metrics);
    io.emit('metrics', metrics);
    io.emit('ip-data', ipData);
}

// Diffuser les métriques toutes les 2 secondes
setInterval(broadcastMetrics, 2000);

server.listen(PORT, () => {
    console.log(`Mimosa API en écoute sur http://localhost:${PORT}`);
});