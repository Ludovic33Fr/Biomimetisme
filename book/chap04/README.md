# Chapitre 4 - Algorithmes évolutionnaires et optimisation

Code d'illustration du Chapitre 4 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`onemax/`](onemax/AlgorithmeGenetique.java) | Algorithme génétique canonique | One-Max (maximiser les 1 d'une chaîne binaire) |
| [`rosenbrock/`](rosenbrock/StrategieEvolution.java) | Stratégie d'évolution (1+1)-ES | Minimisation de la fonction de Rosenbrock |
| [`operateurs/`](operateurs/Operateurs.java) | Catalogue des opérateurs génétiques | Sélection (roulette, rang, tournoi), croisement (1-point, 2-points, uniforme), mutation |
| [`tsp/`](tsp/AlgorithmeGenetiqueTSP.java) | AG appliqué à l'optimisation combinatoire | Problème du voyageur de commerce avec croisement OX |
| [`nsga2/`](nsga2/NSGA2.java) | Optimisation multi-objectif | Tri non-dominé et distance de crowding (NSGA-II) |

## Exécution

Chaque fichier contient une méthode `main` et peut être compilé et exécuté indépendamment :

```bash
javac onemax/AlgorithmeGenetique.java
java -cp . onemax.AlgorithmeGenetique
```
