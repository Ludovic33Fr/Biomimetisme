# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Vue d'ensemble

Monorepo "portefeuille" regroupant des applicatifs inspirés du biomimétisme (chacun illustre une analogie nature → cyber/infra) **plus** le code d'illustration d'un livre **Biomimétisme Informatique**. Il n'y a **pas** de package manager racine ni de tooling commun : chaque app sous `apps/` et chaque chapitre sous `book/` est autonome. Le tableau d'analogies dans `README.md` (Mimosa = repli, Mycélium = partage d'IOCs, Pissenlit = multi-région, etc.) sert de boussole conceptuelle pour les apps.

```
apps/
  myco-ioc/          TypeScript + NATS — partage distribué d'IOCs (mycélium)
  mimosa-api/        TypeScript + Express + Socket.IO — rate limiter (mimosa pudica)
  mimosa-demo/       Docker/k8s + Falco — démo IPS inline + kill endpoint
  EP-Catalog/        Spring Boot 3 / Java 17 — catalogue REST + classe Steg (stéganographie)
  Cybersec/          Fichiers Java autonomes — Steg, GA pour segmentation firewall
  release-automation/  Workflow n8n (pas de build, JSON + docker-compose)
book/
  chap01..chap11/    Code Java pédagogique, un sous-dossier par sujet par chapitre
```

## Commandes par app

### apps/myco-ioc (le plus actif — voir architecture détaillée plus bas)

```bash
# depuis apps/myco-ioc
docker compose up -d --build
docker compose up -d --scale node=4      # scaler les nodes
docker compose down -v                    # reset complet
```

Build TypeScript local d'un service : `cd controller && npm install && npm run build && npm start` (idem `node/`, `traffic/`).

Versioning (la version est codée en dur dans `controller/src/index.ts` puis affichée dans le dashboard à `http://localhost:3000`) :

```bash
# Windows
apps\myco-ioc\version-bump.bat patch|minor|major
# ou
node apps/myco-ioc/version-bump.js patch
```

Après bump : `docker compose down && docker compose up -d --build`.

### apps/mimosa-api

```bash
cd apps/mimosa-api/api
npm install && npm run build && npm start    # prod
npm run dev                                   # ts-node
# ou conteneurisé :
cd apps/mimosa-api && docker compose up --build
```

Variables clés : `PORT`, `WINDOW_S`, `THRESHOLD_RPS`, `PATH_DIVERSITY`, `TRIP_MS`. Dashboard : `http://localhost:8080`. Une IP est repliée (« trip ») uniquement si `RPS > THRESHOLD_RPS` **ET** `path_diversity > PATH_DIVERSITY` en 1 s — ne pas modifier l'un sans l'autre. Beaucoup de scripts `test-*.ps1`/`.sh` à la racine de `api/` pour reproduire des scénarios.

### apps/EP-Catalog (Spring Boot, wrapper Maven inclus)

```bash
# depuis apps/EP-Catalog
.\mvnw.cmd compile                # ou .\compile.bat
.\mvnw.cmd spring-boot:run        # ou .\run.bat
.\mvnw.cmd test                   # tests JUnit
.\mvnw.cmd test -Dtest=ClassName#methodName   # un test précis
.\build-jar.bat                   # JAR exécutable -> target/EP-Catalog-0.0.1-SNAPSHOT.jar
.\docker-build.bat                # build + run via docker-compose.yml
```

Java 17 obligatoire (déclaré dans `pom.xml`). Données seed dans `src/main/resources/data/products.json`, scripts Python d'aide dans `scripts/` (`python add-product.py add|list`).

### apps/mimosa-demo (démo Linux uniquement)

Pas de code à compiler. Deux chemins : `Docker/docker-compose.yml` (NGINX + Flask + Falco eBPF) ou `k8s/` (Deployments + Falco DaemonSet). Falco doit pouvoir charger eBPF sur l'hôte (`--modern-bpf`).

### apps/Cybersec (fichiers Java isolés)

```bash
javac Steg.java
java Steg encode in.png out.png "message"
java Steg decode out.png
```

`GAFirewallSegmentation.java` consomme `flows_ga_example.csv` + `segmentation_rules.txt`.

### apps/release-automation

Workflows n8n (`docker-compose up -d`), pas de build. Credentials attendus dans `n8n/credentials/*.json` (placeholders à remplir, jamais commiter de vrais secrets).

### book/chap01..chap11

Java standard sans build system. Chaque sous-dossier d'un chapitre = un `.java` autoporteur avec un `main`.

```bash
cd book/chap04
javac onemax/AlgorithmeGenetique.java
java -cp . onemax.AlgorithmeGenetique
```

Les chapitres sont essentiellement pédagogiques — éviter d'introduire dépendances externes ou frameworks ; rester en Java standard, une `main` par fichier.

## Architecture détaillée — myco-ioc

C'est le système le plus complexe et celui qui requiert de lire plusieurs fichiers pour comprendre. Métaphore : un **mycélium** qui propage des **IOCs** (indicators of compromise) entre **nodes** (arbres) via un **controller** (réseau mycorhizien) sur un bus **NATS**.

**Services Docker** (`apps/myco-ioc/docker-compose.yml`) :
- `bus` : NATS 2.10 (4222 client / 8222 monitoring)
- `node` : détection locale + blocklist TTL + abonnements aux IOCs partagés (scalable)
- `controller` : agrège votes, applique quorum pondéré, propage, sert le dashboard HTTP+WebSocket sur `:3000`
- `traffic` : générateur de trafic normal + bursts d'attaque
- `natsbox` : utilitaire `nats` CLI pour injecter manuellement

**Topics NATS — contrat à respecter pour toute évolution** :

| Topic | Producteur | Consommateur | Rôle |
|-------|------------|--------------|------|
| `traffic.http` | traffic / natsbox | node (filtre par `nodeId`) | événement HTTP simulé |
| `traffic.control` | controller | traffic | start/stop/low/normal/attack |
| `nodes.hello` | node (toutes les 5 s) | controller | présence + heartbeat |
| `hb.<nodeId>` | node | controller | heartbeat (isolation si > 15 s) |
| `alerts.<nodeId>` | node | controller | détection locale |
| `drops.<nodeId>` | node | controller | requête bloquée par blocklist |
| `ioc.local` | node | controller | candidat IOC, soumis au quorum |
| `ioc.share` | controller | node (tous) | IOC validé à appliquer |
| `ioc.sync.request` (req/reply) | node au démarrage | controller | resynchro des IOCs actifs |
| `ack.<nodeId>` | node | controller | accusé d'application d'un IOC |
| `metrics.controller` | controller (toutes les 10 s) | externe | export SLO |

**Flux nominal** : `traffic.http` → node détecte un burst (≥ `THRESH` essais en `WINDOW_MS` sur `BAD_PATHS`/`BAD_STATUS`) → publie `alerts.<id>` + `ioc.local` + bloque localement → controller agrège votes (quorum **pondéré par réputation** des sources) → si seuil atteint, publie `ioc.share` + persiste dans `state.activeIOCs` avec TTL → tous les nodes appliquent et envoient `ack.<id>`. Le node détecteur **n'est pas isolé** (`health` reste `ok`/`protected`) — c'est volontaire, ne pas changer ce comportement sans intention claire.

**État du controller** (`controller/src/index.ts`, ~1015 lignes — fichier monolithique) : tout est en mémoire dans une seule struct `state`. Les sections clés :
- métriques SLO (MTTD ≤ 2 s, MTTR ≤ 3 s, containment ≤ 10 s) — alertes après 3 violations consécutives
- détection « IOC flood » (> `IOC_FLOOD_THRESHOLD` IOC/s) → augmente le quorum, retombe à la moitié du seuil
- réputation par node (`[0..1]`, défaut 0.5, ±0.1/0.05) qui pondère le quorum
- nettoyage périodique des IOCs expirés + métriques 1 min toutes les secondes
- WebSocket bidirectionnel avec le dashboard (commandes : `updateQuorum`, `expireIOC`, `extendIOC`, `quarantineIOC`, `simulateFalsePositive`, `simulateIOCFlood`, `simulateNodeIsolation`, `toggleFailMode`, `trafficControl`)

**Dashboards servis par le controller** : `/` (simple) et `/visual` (avancé avec timeline SLO). Toute modification visuelle se fait dans `controller/public/*.html`.

**Variables d'environnement principales** (cf. `docker-compose.yml`) : `NATS_URL`, `NODE_ID`, `WINDOW_MS`, `THRESH`, `BLOCK_TTL_SEC`, `BAD_PATHS`, `BAD_STATUS`, `QUORUM`, `DEFAULT_TTL_SEC`, `HTTP_PORT`, `MAX_BLOCKLIST_ENTRIES`, `IOC_FLOOD_THRESHOLD`, `FAIL_MODE` (`fail-open`|`fail-closed`).

## Conventions transverses

- **Langue** : commits, README, identifiants UI et logs sont majoritairement en **français**. Conserver cette convention.
- **OS** : développement principal sous **Windows** (scripts `.bat`, `.ps1`). Les chemins absolus utilisent `C:\repoPerso\Biomimetisme\...`.
- **Pas de tests automatisés à la racine** ; les tests existants sont :
  - JUnit dans `EP-Catalog` (`mvnw test`)
  - Scripts manuels PowerShell/Bash dans `mimosa-api/api/test-*.ps1`
- **Aucune CI/CD** configurée à la racine. Pas de linter / formatter partagé.
- Le fichier `notebooks` à la racine est un fichier vide (pas un répertoire) — ne pas y écrire.
