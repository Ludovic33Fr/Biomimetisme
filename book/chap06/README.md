# Chapitre 6 - Réseaux de neurones et apprentissage bio-inspiré

Code d'illustration du Chapitre 6 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`neurone/`](neurone/Neurone.java) | Neurone artificiel | Porte logique ET, avec Sigmoïde/TanH/ReLU |
| [`mlp/`](mlp/PerceptronMulticouche.java) | Perceptron multicouche | Problème XOR avec rétropropagation et init Xavier |
| [`lstm/`](lstm/CelluleLSTM.java) | Cellule LSTM | Portes oubli/entrée/sortie, état cellulaire |
| [`cnn/`](cnn/CoucheConvolution.java) | Couche de convolution | Convolution 2D avec filtres, pas et ReLU |
| [`cnn/`](cnn/Visualisation.java) | Visualisation d'activations | Cartes d'activation en grille niveaux de gris |
| [`hebbien/`](hebbien/ReseauHebbien.java) | Apprentissage hebbien | Règle de Hebb + règle d'Oja (ACP) |
| [`hebbien/`](hebbien/STDPRule.java) | Plasticité STDP | Courbe LTP/LTD selon Δt des spikes |
| [`neat/`](neat/NEAT.java) | Neuroévolution topologique | Spéciation, croisement, mutation de topologies |
| [`dqn/`](dqn/AgentDQN.java) | Deep Q-Network | Politique ε-greedy, mémoire de replay, réseau cible |

## Exécution

```bash
javac neurone/Neurone.java
java -cp . neurone.Neurone
```
