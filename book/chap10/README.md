# Chapitre 10 - Enjeux éthiques et sociétaux

Code d'illustration du Chapitre 10 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`ethique/`](ethique/EthicalGeneticFramework.java) | Ethics by design pour AG | Principes éthiques + supervision à chaque étape |
| [`explicable/`](explicable/ExplainableGeneticAlgorithm.java) | AG explicable | Traçabilité, visualisation, extraction de règles |
| [`equite/`](equite/FairnessAuditSystem.java) | Audit de biais | Détection + opérateurs équitables (mutation, sélection, fitness) |
| [`garde_fous/`](garde_fous/EthicalGuardrailSystem.java) | Garde-fous éthiques | Validation, approbation humaine, détection d'anomalies |

## Note

Ces codes sont **conceptuels** : ils illustrent l'architecture et les responsabilités
mais dépendent d'interfaces abstraites (Population, FitnessFunction, EthicalConstraint...)
à adapter au domaine métier.

## Exécution

```bash
javac garde_fous/EthicalGuardrailSystem.java
java -cp . garde_fous.EthicalGuardrailSystem
```
