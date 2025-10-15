# Configuration du Rate Limiter Mimosa

## Configuration par défaut

Le système utilise maintenant une configuration par défaut de **10 RPS** (au lieu de 3 RPS précédemment).

### Variables d'environnement

| Variable | Description | Valeur par défaut |
|----------|-------------|-------------------|
| `THRESHOLD_RPS` | Seuil RPS par défaut | `10` |
| `WINDOW_S` | Taille de la fenêtre de calcul RPS (secondes) | `10` |
| `PATH_DIVERSITY` | Seuil de diversité des chemins | `5` |
| `TRIP_MS` | Durée de blocage après déclenchement (ms) | `30000` |
| `PORT` | Port du serveur | `8080` |

## Configurations spécifiques par route

Le système permet de définir des configurations différentes pour des routes spécifiques.

### Route de réservation PS5

La route `/api/reserve-ps5` utilise une configuration plus stricte :
- **RPS** : 0.5 (au lieu de 10)
- **Fenêtre** : 5 secondes
- **Diversité** : 2 chemins
- **Blocage** : 60 secondes

### Ajouter une nouvelle configuration de route

Pour ajouter une nouvelle configuration spécifique, modifiez le tableau `routeConfigs` dans `src/index.ts` :

```typescript
const routeConfigs: RouteConfig[] = [
    {
        path: '/api/reserve-ps5',
        config: reservationConfig,
        limiter: new MimosaLimiter(reservationConfig)
    },
    // Ajouter une nouvelle route ici
    {
        path: '/api/sensitive-endpoint',
        config: {
            windowS: 5,
            thresholdRps: 1.0,
            pathDiversity: 2,
            tripMs: 45000
        },
        limiter: new MimosaLimiter({
            windowS: 5,
            thresholdRps: 1.0,
            pathDiversity: 2,
            tripMs: 45000
        })
    }
];
```

## Endpoints de configuration

### GET /api/config

Retourne la configuration actuelle :

```json
{
  "default": {
    "thresholdRps": 10,
    "pathDiversity": 5,
    "tripMs": 30000,
    "windowS": 10
  },
  "routes": [
    {
      "path": "/api/reserve-ps5",
      "config": {
        "windowS": 5,
        "thresholdRps": 0.5,
        "pathDiversity": 2,
        "tripMs": 60000
      }
    }
  ]
}
```

## Exemples d'utilisation

### Test avec la configuration par défaut (10 RPS)

```bash
# Test des routes normales avec 10 RPS
for i in {1..15}; do curl http://localhost:8080/api/data; done
```

### Test de la route de réservation (0.5 RPS)

```bash
# Test de la route de réservation avec 0.5 RPS
for i in {1..3}; do 
  curl -X POST http://localhost:8080/api/reserve-ps5 \
    -H "Content-Type: application/json" \
    -d '{"email":"test@example.com","name":"Test","phone":"0123456789","quantity":"1"}'
  sleep 2
done
```

## Logs

Les logs indiquent maintenant la route concernée :
- `🚨 IP` : pour les routes par défaut
- `🎮 RÉSERVATION BLOQUÉE` : pour la route de réservation PS5
