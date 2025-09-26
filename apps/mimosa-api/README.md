# 🌿 Mimosa API

> **"Je te touche, tu te refermes"** - Système de protection anti-DDoS inspiré du mimosa pudique

## 📋 Description

Mimosa API est un système de limitation de débit (rate limiting) intelligent qui s'inspire du comportement défensif de la plante mimosa pudique. Quand elle est touchée, la plante se referme pour se protéger, puis se rouvre une fois le danger passé.

Ce système implémente une logique similaire pour protéger les APIs contre les attaques DDoS et les surcharges :

- **Détection intelligente** : Surveille le RPS (requêtes par seconde) et la diversité des chemins
- **Repli défensif** : Bloque temporairement les IPs suspectes
- **Récupération automatique** : Rouvre l'accès après un délai configurable
- **Dashboard temps réel** : Interface web pour surveiller l'état du système

## 🏗️ Architecture

```
mimosa-api/
├── api/
│   ├── src/
│   │   ├── index.ts          # Serveur Express + WebSocket
│   │   ├── index.html        # Dashboard web
│   │   ├── limiter.ts        # Logique du limiteur Mimosa
│   │   └── types.ts          # Types TypeScript
│   ├── public/
│   │   └── app.js            # Client JavaScript du dashboard
│   ├── package.json          # Dépendances Node.js
│   ├── tsconfig.json         # Configuration TypeScript
│   └── Dockerfile            # Container Docker
└── docker-compose.yml        # Orchestration des services
```

## 🚀 Installation et Démarrage

### Prérequis
- Node.js 18+ ou Docker
- npm ou yarn

### Installation locale

```bash
cd apps/mimosa-api/api
npm install
npm run build
npm start
```

### Avec Docker

```bash
cd apps/mimosa-api
docker compose up --build
```

### Mode développement

```bash
cd apps/mimosa-api/api
npm run dev
```

## ⚙️ Configuration

Le système se configure via des variables d'environnement :

| Variable | Description | Défaut |
|----------|-------------|---------|
| `PORT` | Port du serveur | `8080` |
| `WINDOW_S` | Taille de la fenêtre de mesure (secondes) | `10` |
| `THRESHOLD_RPS` | Seuil RPS déclenchant le repli | `10` |
| `PATH_DIVERSITY` | Nombre de chemins distincts en 1s | `5` |
| `TRIP_MS` | Durée du repli (millisecondes) | `30000` |

### Exemple de configuration

```bash
export PORT=3000
export THRESHOLD_RPS=20
export PATH_DIVERSITY=8
export TRIP_MS=60000
```

## 🎯 Utilisation

### Endpoints API

#### `GET /`
Dashboard web interactif avec métriques en temps réel.

#### `GET /api/data`
Endpoint "normal" pour tester le système.

#### `GET /api/noisy/:id`
Endpoint "bruyant" pour simuler des attaques (diversité de chemins).

#### `GET /api/state`
État actuel du système (JSON).

#### `GET /api/config`
Configuration actuelle du limiteur.

### Exemples de test

```bash
# Requêtes normales
curl http://localhost:8080/api/data

# Simulation d'attaque (diversité de chemins)
for i in {1..10}; do
  curl http://localhost:8080/api/noisy/$i
done

# Vérification de l'état
curl http://localhost:8080/api/state
```

## 📊 Dashboard

Le dashboard web (`http://localhost:8080`) affiche :

- **Cartes par IP** : État, RPS, graphiques d'évolution
- **Statistiques globales** : IPs actives, repliées, total requêtes
- **Configuration** : Seuils actuels du système
- **Animations** : Effets visuels pour les replis et récupérations

### Fonctionnalités du dashboard

- ✅ **Métriques temps réel** via WebSocket
- 📈 **Graphiques d'évolution** du RPS par IP
- 🎨 **Animations fluides** pour les changements d'état
- 📱 **Design responsive** pour mobile et desktop
- 🔄 **Mise à jour automatique** toutes les secondes

## 🧠 Logique du Limiteur

### Algorithme Mimosa

1. **Mesure** : Compte les requêtes par IP dans une fenêtre glissante
2. **Détection** : Identifie les patterns suspects (RPS élevé + diversité de chemins)
3. **Repli** : Bloque temporairement l'IP suspecte
4. **Récupération** : Rouvre l'accès après le délai configuré

### Critères de repli

Une IP est repliée si :
- Le RPS dépasse `THRESHOLD_RPS` **ET**
- Le nombre de chemins distincts dépasse `PATH_DIVERSITY` en 1 seconde

### Exemple de comportement

```
IP 192.168.1.100:
- 15 requêtes/seconde sur /api/data ✅ OK
- 8 requêtes/seconde sur 5 chemins différents ✅ OK  
- 12 requêtes/seconde sur 6 chemins différents 🚨 REPLI
```

## 🔧 Développement

### Structure du code

- **`limiter.ts`** : Logique métier du limiteur
- **`index.ts`** : Serveur Express + WebSocket
- **`types.ts`** : Définitions TypeScript
- **`app.js`** : Client JavaScript du dashboard

### Ajout de fonctionnalités

1. Modifier la logique dans `limiter.ts`
2. Ajouter les types dans `types.ts`
3. Exposer via l'API dans `index.ts`
4. Mettre à jour le dashboard dans `app.js`

### Tests

```bash
# Tests unitaires (à implémenter)
npm test

# Tests d'intégration
npm run test:integration

# Tests de charge
npm run test:load
```

## 🐳 Docker

### Build de l'image

```bash
docker build -t mimosa-api ./api
```

### Exécution

```bash
docker run -p 8080:8080 \
  -e THRESHOLD_RPS=15 \
  -e PATH_DIVERSITY=6 \
  mimosa-api
```

### Docker Compose

```yaml
version: '3.8'
services:
  mimosa-api:
    build: ./api
    ports:
      - "8080:8080"
    environment:
      - THRESHOLD_RPS=15
      - PATH_DIVERSITY=6
      - TRIP_MS=30000
```

## 📈 Monitoring

### Métriques disponibles

- **RPS par IP** : Requêtes par seconde
- **État des IPs** : Ouvert/Replié
- **Diversité des chemins** : Nombre de chemins distincts
- **Durée des replis** : TTL restant
- **Statistiques globales** : Totaux et moyennes

### Intégration monitoring

Le système expose des événements WebSocket pour l'intégration :

```javascript
socket.on('metrics', ({ ip, rps }) => {
  // Mise à jour des métriques
});

socket.on('trip', ({ ip, ttlMs, reason }) => {
  // Notification de repli
});

socket.on('recover', ({ ip }) => {
  // Notification de récupération
});
```

## 🛡️ Sécurité

### Bonnes pratiques

- ✅ Limitation de débit par IP
- ✅ Détection de patterns suspects
- ✅ Blocage temporaire automatique
- ✅ Récupération progressive
- ✅ Monitoring en temps réel

### Limitations

- ⚠️ Basé sur l'IP (peut être contourné avec des proxies)
- ⚠️ Pas de whitelist/blacklist persistante
- ⚠️ Configuration statique (pas de ML)

## 🤝 Contribution

1. Fork le projet
2. Créer une branche feature (`git checkout -b feature/amazing-feature`)
3. Commit les changements (`git commit -m 'Add amazing feature'`)
4. Push vers la branche (`git push origin feature/amazing-feature`)
5. Ouvrir une Pull Request

## 📄 Licence

Ce projet fait partie du projet Biomimetisme et est sous licence MIT.

## 🙏 Inspiration

Inspiré du comportement défensif de la **Mimosa pudica** (mimosa pudique), une plante qui se referme instantanément quand elle est touchée, puis se rouvre progressivement une fois le danger passé.

> *"La nature nous enseigne que la meilleure défense est parfois de se replier temporairement pour mieux renaître."*

---

**🌿 Mimosa API** - *Protection naturelle pour vos APIs*
