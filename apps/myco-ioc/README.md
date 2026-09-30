# Myco-IOC (Réseau mycorhizien → Partage d’IOCs)

Démo locale multi-conteneurs (Docker) : plusieurs **nodes** (arbres) détectent des comportements anormaux, publient des **IOCs**, qu’un **controller** propage à l’écosystème via **NATS** (le mycélium). Un **traffic generator** émet du trafic normal + attaques.

## Prérequis
- Docker & Docker Compose

## Démarrage
```bash
docker compose up -d --build
docker compose up -d --scale node=4
```
- **Projection** : http://localhost:3000 — la vue à montrer. Topologie du mycélium en plein écran, propagation animée, légende, récit en quatre temps, pupitre de démo.
- **Opérateur** : http://localhost:3000/visual — SLO, table des indicateurs actifs, quorum, santé des arbres, simulations.
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

La version s'affiche dans l'en-tête des deux pages (http://localhost:3000 et http://localhost:3000/visual).

## Ce qui se passe
- Un node voit ≥ 20 essais / 5s depuis la même IP sur un chemin sensible → **alert** + **ioc.local** + blocage local
- Le controller propage en **ioc.share** (quorum configurable) → les autres nodes appliquent le blocage (TTL)
- Le node détecteur reste opérationnel (pas d'isolation) et tous les nodes sont protégés collectivement
- Les drops sont visibles dans les logs des nodes (`drops.<nodeId>`)

## Les deux vues

Le controller sert deux dashboards qui partagent le même socle (`controller/public/shared/`) mais ne répondent pas à la même question.

### `/` — Projection

Ce qu'on met au vidéoprojecteur. La topologie occupe le premier écran :

- **Thalle central** = le controller, le tissu mycélien qui relaie les indicateurs.
- **Arbres en couronne**, colorés par état : sain, **a détecté l'attaque**, **vacciné par le réseau**, coupé du mycélium. Une légende permanente est affichée sous le dessin.
- **Propagation animée** : à chaque `ioc.share`, une spore part de l'arbre détecteur, traverse le thalle et irrigue les autres arbres. Chaque arbre ne change de couleur qu'à l'arrivée de son `ack` réel — l'écran ne promet rien avant que ce soit vrai.
- **Arc de TTL** autour de chaque arbre protégé : il se résorbe à mesure que la protection expire.
- **Récit en quatre temps** sous la topologie : qui a détecté, combien de voix sur le quorum, combien d'arbres ont appliqué, et le délai détection → partage.

Le pupitre (démo guidée + contrôles de trafic) est en haut. La démo guidée est interruptible.

**Remettre à zéro** (présent sur les deux vues, confirmation en deux clics) ramène le système à l'état initial : aucune protection nulle part, aucune mesure, aucun historique, trafic arrêté. C'est ce qu'il faut entre deux démos — sinon la seconde démarre avec des arbres déjà vaccinés et l'histoire ne se raconte plus.

À noter : vider l'état du controller ne suffit pas, les blocklists vivent dans la mémoire de chaque node. Le reset publie donc `ioc.flush` sur le bus pour que chaque arbre lève ses propres blocages.

### `/visual` — Opérateur

- Bande de 5 mesures avec leur cible (MTTD, MTTR, confinement, couverture, débit d'IOC).
- Table des indicateurs actifs : voix recueillies / quorum, propagation (acks / arbres), protection restante, actions (**Expirer**, **+60 s**, **Bloquer 24 h** — les actions sans retour en arrière demandent une confirmation en deux clics).

  Ces trois actions portent sur **l'indicateur** (l'adresse IP bloquée), jamais sur un arbre : elles ne font qu'allonger ou raccourcir sa durée de vie. Aucune ne coupe un nœud du réseau — l'état gris « coupé du mycélium » vient uniquement d'une perte de heartbeat.
- Santé des arbres, alertes de service, réglage du quorum, mode de repli, simulations.

Aucune valeur affichée n'est reconstituée côté navigateur : tout vient du payload du controller.

### Socle partagé

`controller/public/shared/` : `theme.css` (jetons de couleur, un seul jeu), `bus.js` (client WebSocket avec backoff), `mycelium.js` (topologie + propagation), `ui.js`, `format.js`, `icons.js`. Aucune dépendance externe, aucun CDN : les pages fonctionnent sans accès réseau.

## Services
- **bus** : NATS
- **node** : détection locale + blocklist TTL + souscription aux IOC partagés
- **controller** : agrégation (quorum) + propagation + WebSocket état + interface web
- **traffic** : trafic normal + bursts réguliers
- **natsbox** : utilitaires `nats` CLI pour tester

## Changer le nombre d'arbres

```bash
docker compose up -d --scale node=2 --no-recreate   # réduire
docker compose up -d --scale node=6 --no-recreate   # augmenter
```

`--no-recreate` évite de redémarrer le controller et le bus au passage.

**À la montée**, le nouvel arbre apparaît dans les secondes qui suivent (il émet `nodes.hello` toutes les 5 s) et le journal affiche une ligne « a rejoint le mycélium ».

**À la descente**, les conteneurs disparaissent mais le controller ne peut pas savoir tout de suite si c'est un arrêt définitif ou un redémarrage. Il applique donc deux seuils :

| Silence | État |
|---|---|
| > 15 s | l'arbre passe **« coupé du mycélium »** (gris) — il peut encore revenir |
| > 120 s | l'arbre est **retiré de la topologie**, avec une ligne au journal |

Pour ne pas attendre, le bouton **Remettre à zéro** évacue immédiatement tout arbre silencieux depuis plus de 15 s.

## Reset complet de la stack

```bash
docker compose down -v
docker compose up -d --build --scale node=4
```
