# myco-ioc — Reste à faire

État au 2026-05-10. Le **Lot A** et les blocs **B1, B3, B4, B5** du Lot B sont livrés et pushés sur `main`. Le filet de sécurité (`tests/smoke.mjs`) reste vert sur tous les paliers.

## Lot B — reste

### ~~B2 — Mode démo simplifié~~ *(livré autrement)*

Le besoin — « ne garder que ce qui raconte l'analogie mycélium » — est couvert par la
refonte des dashboards : `/` est la vue de projection (topologie, propagation, récit,
pupitre) et `/visual` la vue opérateur (SLO, quorum, simulations). Deux URL au lieu
d'un toggle et d'un `localStorage`, et chaque page est complète pour son usage.

### Bug pré-existant repéré pendant les tests

- Le `node` log une stacktrace complète par message `traffic.http` malformé (cf. commit B5 qui n'a pas changé ce comportement). Pour une démo robuste, dégrader le log en `log.warn("malformed traffic event", { err })` sans stack — quand un payload externe est cassé, ça inonde les logs et masque les vraies erreurs.

## Lot C — visuel mycélium

### ~~C1 — Topologie en thalle + particules~~ *(livré)*
Thalle organique au centre, arbres en couronne elliptique, layout radial déterministe
(plus de force-simulation qui s'agite en permanence). Chaque `ioc.share` envoie une spore
de l'arbre détecteur vers le thalle puis vers chaque voisin ; la cible ne change de
couleur qu'à réception de son `ack`. Voir `controller/public/shared/mycelium.js`.

### ~~C2 — Halo TTL~~ *(livré)*
Arc qui se résorbe autour de chaque arbre protégé, proportionnel au temps restant sur
l'IOC appliqué le plus long.

### C3 — « Phéromone trail » *(reste à faire)*
- Épaisseur des hyphes proportionnelle au volume d'IOCs partagés sur les 60 dernières secondes (analogie fourmis / blob de Physarum).
- Demande un historique côté controller : aujourd'hui le payload ne porte que l'instantané.

### C4 — Arbres en feuilles plutôt qu'en disques *(reste à faire)*
Les arbres sont rendus en disques colorés. Une silhouette de feuille (SVG dessiné, pas
d'emoji) porterait mieux la métaphore, à condition de garder la lisibilité à distance
et la zone de focus clavier.

## Améliorations potentielles (non priorisées)

- **Tests d'intégration éphémères** : aujourd'hui le smoke test attend une stack courante. Une variante `npm run test:ephemeral` qui spawn une compose dédiée + tear down — utile en CI.
- ~~**Reset complet de démo**~~ *(livré)* : bouton « Remettre à zéro » sur les deux vues. Purge l'état du controller (`resetDemoState`), **les blocklists locales des nodes via le nouveau topic `ioc.flush`** — le point que la version envisagée dans ce TODO aurait manqué — et arrête le trafic.
- ~~**Pause / resume de la démo guidée**~~ *(livré en partie)* : la démo est interruptible (`stopGuidedDemo`, bouton « Interrompre » dans la bande de narration). Pas de reprise à l'étape courante.
