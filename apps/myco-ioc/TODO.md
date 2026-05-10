# myco-ioc — Reste à faire

État au 2026-05-10. Le **Lot A** et les blocs **B1, B3, B4, B5** du Lot B sont livrés et pushés sur `main`. Le filet de sécurité (`tests/smoke.mjs`) reste vert sur tous les paliers.

## Lot B — reste

### B2 — Mode démo simplifié *(petit, prêt à attaquer)*

Toggle UI qui masque les widgets SLO/réputation/fail-mode pour ne garder que ce qui raconte l'analogie mycélium : topologie, IOCs actifs, timeline d'événements, contrôles trafic, narration de la démo guidée.

- Bouton compact dans le header de `visual.html` : **« 🌱 Mode démo »** (par défaut) / **« 🛰 Mode avancé »**.
- En mode démo, masquer (via `display: none` sur les `.panel`) :
  - KPI MTTD/MTTR/Containment Time/Containment Ratio
  - Panel « SLO/SLA Monitoring » (les 4 cards SLO)
  - Panel « SLO Alerts »
  - Panel « Couverture IOC » (donut)
  - Panel « Santé & Synchronisation »
  - Panel « Sparklines par Nœud »
  - Panel « Contrôles » (slider quorum + toggle fail-mode + boutons simulate*)
- Préférence persistée en `localStorage` (clé `myco-mode`).
- Pas de changement backend nécessaire — purement UI.

Tradeoff : on garde tout le code SLO/réputation/fail-mode actif en arrière-plan ; seules les visualisations sont cachées. Une variante plus radicale serait un flag `DEMO_MODE=true` côté controller qui désactive aussi les checks SLO — overkill pour la pédagogie.

### Bug pré-existant repéré pendant les tests

- Le `node` log une stacktrace complète par message `traffic.http` malformé (cf. commit B5 qui n'a pas changé ce comportement). Pour une démo robuste, dégrader le log en `log.warn("malformed traffic event", { err })` sans stack — quand un payload externe est cassé, ça inonde les logs et masque les vraies erreurs.

## Lot C — visuel mycélium *(½ journée, vend la démo)*

Reste à faire dans son intégralité. Voir le diagnostic d'origine pour la motivation : la métaphore mycélium est aujourd'hui invisible dans la topologie D3 force-directed standard.

### C1 — Topologie en thalle + feuilles
- Bus NATS rendu au centre comme un *thalle* (forme organique SVG, pas un nœud rond).
- Nodes périphériques en *feuilles d'arbre* qui se colorient selon `health` (au lieu des cercles uniformes actuels).
- Chaque IOC partagé = une particule lumineuse animée qui voyage du node détecteur, traverse le bus, irrigue les autres feuilles (animation D3 le long des arêtes, ~800 ms).

### C2 — Halo TTL
- Cercle qui se résorbe progressivement autour de chaque feuille tant qu'elle a au moins un IOC actif. Donne le sens *temps* sans regarder un compteur.

### C3 — « Phéromone trail »
- Épaisseur des arêtes proportionnelle au volume d'IOCs partagés sur les 60 dernières secondes (analogie fourmis / blob de Physarum).

## Améliorations potentielles (non priorisées)

- **Tests d'intégration éphémères** : aujourd'hui le smoke test attend une stack courante. Une variante `npm run test:ephemeral` qui spawn une compose dédiée + tear down — utile en CI.
- **Reset complet de démo** : un bouton « 🔄 Reset » qui vide `state.activeIOCs`, `state.votes`, `state.metrics.*`, `state.sloAlerts` et publie un `traffic.control stop`.
- **Pause / resume de la démo guidée** : actuellement on ne peut pas annuler une démo en cours (juste attendre les 60s).
