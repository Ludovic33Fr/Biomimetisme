# Chapitre 7 - Systèmes adaptatifs et auto-organisation

Code d'illustration du Chapitre 7 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`regulateur/`](regulateur/RegulateurAdaptatif.java) | Régulateur PID adaptatif | Homéostasie thermique avec auto-tuning |
| [`banc/`](banc/BancDePoissons.java) | Banc de poissons (Reynolds) | Auto-organisation : cohésion/alignement/séparation (Swing) |
| [`reparation/`](reparation/SystemeAutoReparateur.java) | Services auto-réparateurs | Détection + redondance adaptative + régénération |
| [`qlearning/`](qlearning/QLearningNavigationGrid.java) | Q-learning en grille | Navigation par apprentissage par renforcement (Swing) |

## Exécution

```bash
javac qlearning/QLearningNavigationGrid.java
java -cp . qlearning.QLearningNavigationGrid
```
