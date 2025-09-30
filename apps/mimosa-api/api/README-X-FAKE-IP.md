# Simulation d'IP avec X-Fake-IP

## Vue d'ensemble

Cette fonctionnalité permet de simuler des adresses IP différentes via le header HTTP `X-Fake-IP` pour tester le système de limitation de Mimosa sans avoir besoin de multiples machines ou configurations réseau complexes.

## Fonctionnement

Quand une requête contient le header `X-Fake-IP`, le système utilise cette valeur comme adresse IP au lieu de l'IP réelle de la requête. Chaque IP simulée est traitée indépendamment avec son propre état de limitation.

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

### Avec PowerShell

```powershell
# Requête normale
Invoke-WebRequest -Uri "http://localhost:8080/api/data"

# Requête avec IP simulée
Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="192.168.1.100"}

# Test de déclenchement
for ($i = 1; $i -le 15; $i++) {
    Invoke-WebRequest -Uri "http://localhost:8080/api/data" -Headers @{"X-Fake-IP"="10.0.0.1"}
    Start-Sleep -Milliseconds 100
}
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

## Scripts de test

### Scripts PowerShell

- `demo-simulation.ps1` - Démonstration complète de la fonctionnalité
- `test-simulation.ps1` - Tests de base avec IPs simulées
- `test-limiter-trigger.ps1` - Tests de déclenchement du limiteur
- `test-sustained.ps1` - Tests avec requêtes soutenues
- `test-extreme.ps1` - Tests avec requêtes extrêmes
- `test-aggressive.ps1` - Tests agressifs avec jobs parallèles

### Scripts Node.js

- `test-simulation.js` - Script de test en Node.js

### Scripts Bash

- `test-curl.sh` - Tests avec curl (Linux/macOS)

## Exemples de résultats

### IPs simulées normales
```
IPs NORMALES:
  - 192.168.1.100: RPS=0.1
  - 10.0.0.50: RPS=0.1
  - 192.168.1.200: RPS=1.5
```

### IPs bloquées
```
IPs BLOQUEES:
  - 192.168.1.500: RPS=10, TTL=19260ms
    Raison: RPS>=10 (actuel~10.0)
  - 192.168.1.501: RPS=0.5, TTL=22508ms
    Raison: Diversité chemins>=5 (actuel=5)
```

## Logs du serveur

Quand une IP simulée est utilisée, le serveur log :
```
🎭 Simulation IP: 192.168.1.100 (IP réelle: 127.0.0.1)
📡 Émission WebSocket metrics: IP=192.168.1.100, RPS=2.5
🚨 IP 10.0.0.1 tripped: RPS>=10 (actuel≈12.3) (TTL: 30000ms)
```

## Configuration

La simulation utilise la même configuration que le système normal :

- `WINDOW_S` : Taille de la fenêtre de calcul RPS (défaut: 10s)
- `THRESHOLD_RPS` : Seuil RPS pour déclencher le repli (défaut: 10)
- `PATH_DIVERSITY` : Nombre de chemins distincts pour déclencher le repli (défaut: 5)
- `TRIP_MS` : Durée du repli en millisecondes (défaut: 30000ms)

## Cas d'usage

### Développement
- Tester différents scénarios sans changer d'IP
- Déboguer des problèmes spécifiques à une IP
- Valider le comportement du système de limitation

### Démonstration
- Montrer le comportement avec des IPs bloquées vs non bloquées
- Expliquer le fonctionnement du système de limitation
- Tester les seuils et la récupération

### Tests automatisés
- Créer des tests reproductibles
- Valider les différents cas de déclenchement
- Tester la récupération automatique

## Avantages

1. **Simplicité** : Pas besoin de multiples machines ou configurations réseau
2. **Reproductibilité** : Tests identiques à chaque exécution
3. **Flexibilité** : Simulation de n'importe quelle IP
4. **Isolation** : Chaque IP simulée est indépendante
5. **Debugging** : Logs clairs pour identifier les IPs simulées

## Limitations

1. **Header requis** : Nécessite l'ajout du header `X-Fake-IP`
2. **Client-side** : Le client doit supporter l'ajout de headers
3. **Sécurité** : En production, considérer les implications de sécurité

## Sécurité

En production, considérer :
- Valider que seuls les tests autorisés peuvent utiliser cette fonctionnalité
- Limiter l'utilisation aux environnements de test
- Ajouter une authentification pour l'utilisation du header `X-Fake-IP`

## Dashboard

Le dashboard à `http://localhost:8080` affiche :
- Toutes les IPs (réelles et simulées)
- Leur état (bloquées ou normales)
- Leur RPS actuel
- Le TTL des IPs bloquées
- La raison du blocage

## Conclusion

La fonctionnalité `X-Fake-IP` est un outil puissant pour tester et démontrer le système de limitation Mimosa. Elle permet de simuler facilement différents scénarios d'utilisation et de valider le comportement du système sans complexité réseau.
