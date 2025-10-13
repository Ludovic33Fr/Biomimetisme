# Myco-IOC (Réseau mycorhizien → Partage d’IOCs)

Démo locale multi-conteneurs (Docker) : plusieurs **nodes** (arbres) détectent des comportements anormaux, publient des **IOCs**, qu’un **controller** propage à l’écosystème via **NATS** (le mycélium). Un **traffic generator** émet du trafic normal + attaques.

## Prérequis
- Docker & Docker Compose

## Démarrage
```bash
docker compose up -d --build
docker compose up -d --scale node=4
```
- **Interface web** : http://localhost:3000 (dashboard simple)
- **Interface visuelle** : http://localhost:3000/visual (dashboard avancé avec métriques)
- NATS monitoring : http://localhost:8222

## Injection d’un burst manuel (optionnel)
```bash
docker compose exec natsbox sh -lc '
for i in $(seq 1 30); do
  nats -s nats://bus:4222 pub traffic.http   "{"nodeId":"$(hostname)","ts":$(( $(date +%s%3N) )),"src_ip":"203.0.113.66","path":"/wp-login.php","status":401}"
done
'
```

## Versioning

Le système inclut un numéro de version qui s'affiche sur les pages web pour valider que vous testez la bonne version.

### Incrémenter la version

**Windows:**
```bash
version-bump.bat patch    # 2.1.0 → 2.1.1
version-bump.bat minor    # 2.1.0 → 2.2.0  
version-bump.bat major    # 2.1.0 → 3.0.0
```

**Linux/Mac:**
```bash
node version-bump.js patch    # 2.1.0 → 2.1.1
node version-bump.js minor    # 2.1.0 → 2.2.0
node version-bump.js major    # 2.1.0 → 3.0.0
```

### Vérifier la version

La version s'affiche en haut des pages :
- **Dashboard Simple** : http://localhost:3000
- **Dashboard Visuel** : http://localhost:3000/visual

## Ce qui se passe
- Un node voit ≥ 20 essais / 5s depuis la même IP sur un chemin sensible → **alert** + **ioc.local** + blocage local
- Le controller propage en **ioc.share** (quorum configurable) → les autres nodes appliquent le blocage (TTL)
- Le node détecteur reste opérationnel (pas d'isolation) et tous les nodes sont protégés collectivement
- Les drops sont visibles dans les logs des nodes (`drops.<nodeId>`)

## Interface Web

L'interface web (http://localhost:3000) affiche une **topologie réseau interactive** avec :

- **Nœuds** : services (node-1…n) avec couleurs selon l'état :
  - 🟢 Vert = OK (opérationnel)
  - 🔵 Bleu = Protected (IOC appliqué, protection active)
  - 🔴 Rouge = Isolé (problème de connectivité)
  - 🟠 Orange = Drops élevés (activité suspecte)

- **Anneaux TTL** : arcs qui décroissent jusqu'à l'expiration du dernier IOC actif

- **Arêtes** : pulsation courte quand un ioc.share passe (visualiser la diffusion)

- **Tooltips** : alerts_1m, drops_1m, derniers IOC appliqués, latence bus estimée

## Services
- **bus** : NATS
- **node** : détection locale + blocklist TTL + souscription aux IOC partagés
- **controller** : agrégation (quorum) + propagation + WebSocket état + interface web
- **traffic** : trafic normal + bursts réguliers
- **natsbox** : utilitaires `nats` CLI pour tester

## Scale & reset
```bash
docker compose up -d --build
docker compose up -d --scale node=6
docker compose down -v
```
