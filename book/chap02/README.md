# Chapitre 2 - L'évolution comme processus d'optimisation

Code d'illustration du Chapitre 2 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`darwin/`](darwin/EvolutionDarwinienne.java) | Évolution darwinienne canonique | Maximisation du nombre de 1 dans un génome binaire |
| [`tsp/`](tsp/AlgorithmeGenetiqueTSP.java) | Algorithme génétique | Voyageur de commerce avec croisement OX et mutation par inversion |
| [`strategie/`](strategie/StrategieEvolutionnaire.java) | Stratégie évolutionnaire (μ, λ) | Minimisation de la fonction de Rosenbrock avec auto-adaptation de sigma |
| [`pso/`](pso/EssaimsParticulaires.java) | Essaims particulaires (PSO) | Minimisation de la fonction de Rastrigin |
| [`aco/`](aco/ColonieFourmis.java) | Colonies de fourmis (ACO) | Voyageur de commerce avec phéromones et heuristique |
| [`coevolution/`](coevolution/EcosystemeCoevolution.java) | Co-évolution prédateurs-proies | Simulation Swing d'un écosystème dynamique (Lotka-Volterra) |

## Exécution

```bash
javac darwin/EvolutionDarwinienne.java
java -cp . darwin.EvolutionDarwinienne
```
