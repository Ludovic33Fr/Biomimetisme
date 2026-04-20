# Chapitre 5 - Systèmes multi-agents et intelligence collective

Code d'illustration du Chapitre 5 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`agent/`](agent/AgentDemo.java) | Agent réactif + environnement | Interface Agent, ReactiveAgent, Environment avec perception-décision-action |
| [`flocking/`](flocking/FlockingSimulation.java) | Règles de Reynolds | Vol en essaim (séparation, alignement, cohésion) en Swing |
| [`antforaging/`](antforaging/AntForagingSimulation.java) | Stigmergie par phéromones | Recherche de nourriture par fourmis en Swing |
| [`tsp/`](tsp/AntColonyOptimization.java) | ACO pour TSP | Voyageur de commerce avec règle de transition probabiliste |
| [`vrp/`](vrp/AntColonyVRP.java) | ACO pour VRP | Routage de véhicules avec contraintes de capacité |
| [`pso/`](pso/ParticleSwarmOptimization.java) | PSO de base | Optimisation de fonctions continues (Rosenbrock) |
| [`pso/`](pso/FonctionsTest.java) | Fonctions de test PSO | Sphere, Rastrigin, Ackley comme ObjectiveFunction |
| [`portefeuille/`](portefeuille/PortfolioOptimization.java) | PSO appliqué à la finance | Optimisation de portefeuille sous aversion au risque |

## Exécution

```bash
javac pso/*.java
java -cp . pso.ParticleSwarmOptimization
```

Le dossier `portefeuille/` dépend de `pso/` :

```bash
javac -d out pso/*.java portefeuille/PortfolioOptimization.java
java -cp out portefeuille.PortfolioOptimization
```
