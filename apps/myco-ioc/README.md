# Myco-IOC (Réseau mycorhizien → Partage d’IOCs)

Démo locale multi-conteneurs (Docker) : plusieurs **nodes** (arbres) détectent des comportements anormaux, publient des **IOCs**, qu’un **controller** propage à l’écosystème via **NATS** (le mycélium). Un **traffic generator** émet du trafic normal + attaques.

## Prérequis
- Docker & Docker Compose

## Démarrage
```bash
docker compose up -d --build
docker compose up -d --scale node=4
```
- **Interface web** : http://localhost:3000 (topologie réseau + graphique mycélium)
- NATS monitoring : http://localhost:8222

## Injection d’un burst manuel (optionnel)
```bash
docker compose exec natsbox sh -lc '
for i in $(seq 1 30); do
  nats -s nats://bus:4222 pub traffic.http   "{"nodeId":"$(hostname)","ts":$(( $(date +%s%3N) )),"src_ip":"203.0.113.66","path":"/wp-login.php","status":401}"
done
'
```

## Ce qui se passe
- Un node voit ≥ 20 essais / 5s depuis la même IP sur un chemin sensible → **alert** + **ioc.local**
- Le controller propage en **ioc.share** (quorum configurable) → les autres nodes **bloqunt** (TTL)
- Les drops sont visibles dans les logs des nodes (`drops.<nodeId>`)

## Interface Web

L'interface web (http://localhost:3000) affiche une **topologie réseau interactive** avec :

- **Nœuds** : services (node-1…n) avec couleurs selon l'état :
  - 🟢 Vert = OK
  - 🟠 Orange = Attaque en cours (alert locale <60s)
  - 🔵 Bleu = IOC appliqué
  - 🔴 Rouge = Drops élevés

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
docker compose up -d --scale node=6
docker compose down -v
```
