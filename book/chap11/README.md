# Chapitre 11 - Perspectives futures de l'informatique biomimétique

Code d'illustration du Chapitre 11 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`integration/`](integration/IntegrationEngine.java) | Framework multi-paradigmes | Coordination de modules évolutionnaire/neuronal/MAS via bus partagé |
| [`spiking/`](spiking/SpikingNeuralNetwork.java) | Réseau de neurones à impulsions | LIF + STDP pour calcul neuromorphique |
| [`dna/`](dna/DNAComputingSimulation.java) | ADN computing | Chemin hamiltonien à la Adleman |
| [`hyperheuristic/`](hyperheuristic/HyperHeuristicSystem.java) | Hyper-heuristique adaptative | Sélection ε-greedy d'heuristiques selon performances |
| [`emergence/`](emergence/EmergenceAnalysisTool.java) | Analyse d'émergence | Visualisation Swing + métriques (entropie, clusters) |
| [`validator/`](validator/AdaptiveSystemValidator.java) | Validation de systèmes adaptatifs | Framework de tests par scénarios |
| [`loadbalancer/`](loadbalancer/BioInspiredLoadBalancer.java) | Load balancer à phéromones | Routage adaptatif avec ACO + performance |
| [`autonomic/`](autonomic/BioinspiredAutonomicFramework.java) | Framework autonomique MAPE-K | Boucle Monitor-Analyze-Plan-Execute + Knowledge |

## Note

Les codes de ce chapitre sont **représentatifs** : ils illustrent les concepts
prospectifs avec des implémentations compactes et fonctionnelles. Chaque programme
peut être compilé et exécuté indépendamment.

## Exécution

```bash
javac spiking/SpikingNeuralNetwork.java
java -cp . spiking.SpikingNeuralNetwork
```
