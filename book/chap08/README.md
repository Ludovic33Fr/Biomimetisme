# Chapitre 8 - Applications et études de cas

Code d'illustration du Chapitre 8 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`logistique/`](logistique/AntColonyVRP.java) | ACO pour le CVRP | Optimisation de tournées de 20 clients avec 5 véhicules |
| [`radio/`](radio/PneumoniaDetectionCNN.java) | CNN bio-inspiré DL4J | Détection de pneumonies sur radiographies (nécessite Deeplearning4j) |
| [`blob/`](blob/BlobInspiredNetwork.java) | Réseau résilient type Physarum | Auto-organisation + auto-réparation d'un réseau distribué |

## Exécution

Le code ACO et BlobInspiredNetwork sont autonomes :
```bash
javac logistique/AntColonyVRP.java
java -cp . logistique.AntColonyVRP
```

Le CNN DL4J nécessite Maven/Gradle avec les dépendances :
- `org.deeplearning4j:deeplearning4j-core`
- `org.nd4j:nd4j-native-platform`
- `org.datavec:datavec-api`
