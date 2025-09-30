# Simulation d'IP avec X-Fake-IP

Cette fonctionnalité permet de simuler des adresses IP différentes via le header HTTP `X-Fake-IP` pour tester le système de limitation de Mimosa.

## Comment ça marche

Quand une requête contient le header `X-Fake-IP`, le système utilise cette valeur comme adresse IP au lieu de l'IP réelle de la requête. Cela permet de :

- Tester le comportement du limiteur avec différentes IPs
- Simuler des utilisateurs bloqués vs non bloqués
- Déboguer le système sans avoir besoin de multiples machines

## Utilisation

### Avec curl

```bash
# Requête normale (utilise l'IP réelle)
curl http://localhost:8080/api/data

# Requête avec IP simulée
curl -H "X-Fake-IP: 192.168.1.100" http://localhost:8080/api/data

# Test de déclenchement du limiteur
for i in {1..15}; do
  curl -H "X-Fake-IP: 10.0.0.1" http://localhost:8080/api/data
  sleep 0.1
done
```

### Avec JavaScript/Fetch

```javascript
// Requête normale
fetch('http://localhost:8080/api/data')

// Requête avec IP simulée
fetch('http://localhost:8080/api/data', {
  headers: {
    'X-Fake-IP': '192.168.1.100'
  }
})
```

### Avec Node.js/HTTP

```javascript
const http = require('http');

const options = {
  hostname: 'localhost',
  port: 8080,
  path: '/api/data',
  method: 'GET',
  headers: {
    'X-Fake-IP': '192.168.1.100'
  }
};

const req = http.request(options, (res) => {
  // Traitement de la réponse
});
```

## Scripts de test

### Test automatique avec Node.js

```bash
node test-simulation.js
```

Ce script teste :
- IP normale (sans header)
- IP simulée simple
- Déclenchement du limiteur par RPS
- Déclenchement par diversité de chemins

### Test avec curl

```bash
./test-curl.sh
```

Script bash qui fait les mêmes tests avec curl.

## Comportement

1. **Priorité** : Le header `X-Fake-IP` a la priorité sur l'IP réelle
2. **Logs** : Les IPs simulées sont loggées avec l'emoji 🎭
3. **Isolation** : Chaque IP simulée est traitée indépendamment
4. **Dashboard** : Les IPs simulées apparaissent dans le dashboard comme des IPs normales

## Exemple de logs

```
🎭 Simulation IP: 192.168.1.100 (IP réelle: 127.0.0.1)
📡 Émission WebSocket metrics: IP=192.168.1.100, RPS=2.5
🚨 IP 10.0.0.1 tripped: RPS>=10 (actuel≈12.3) (TTL: 30000ms)
```

## Cas d'usage

- **Développement** : Tester différents scénarios sans changer d'IP
- **Débogage** : Reproduire des problèmes spécifiques à une IP
- **Démonstration** : Montrer le comportement du système avec des IPs bloquées/non bloquées
- **Tests automatisés** : Créer des tests reproductibles

## Configuration

La simulation utilise la même configuration que le système normal :

- `WINDOW_S` : Taille de la fenêtre de calcul RPS (défaut: 10s)
- `THRESHOLD_RPS` : Seuil RPS pour déclencher le repli (défaut: 10)
- `PATH_DIVERSITY` : Nombre de chemins distincts pour déclencher le repli (défaut: 5)
- `TRIP_MS` : Durée du repli en millisecondes (défaut: 30000ms)
