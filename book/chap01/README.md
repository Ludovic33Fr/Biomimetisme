# Chapitre 1 - Principes d'organisation du vivant

Code d'illustration du Chapitre 1 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`hierarchie/`](hierarchie/HierarchieBiologique.java) | Modélisation des niveaux d'organisation | Composition cellule → tissu → organe → organisme → population → écosystème |
| [`automate/`](automate/JeuDeLaVie.java) | Auto-organisation et émergence | Jeu de la Vie de Conway (Swing) |
| [`modularite/`](modularite/ReseauMetabolique.java) | Modularité biologique | Réseau métabolique hiérarchique (enzymes, voies, métabolisme) |
| [`homeostasie/`](homeostasie/RegulationTemperature.java) | Boucles de rétroaction | Régulation de la température corporelle (Swing) |
| [`redondance/`](redondance/ReseauNeuronalRedondant.java) | Redondance et robustesse | Réseau neuronal tolérant aux pannes (Swing) |

## Exécution

```bash
javac hierarchie/HierarchieBiologique.java
java -cp . hierarchie.HierarchieBiologique
```
