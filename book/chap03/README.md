# Chapitre 3 - Les écosystèmes comme modèles de systèmes complexes

Code d'illustration du Chapitre 3 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`ecosysteme/`](ecosysteme/EcosystemeSimple.java) | Écosystème trophique 3 niveaux | Simulation Swing producteurs/herbivores/carnivores avec perturbation |
| [`reseau/`](reseau/ReseauTrophique.java) | Réseau trophique en graphe | Dynamique de biomasse sur un food web de 6 espèces |
| [`autoorg/`](autoorg/AutoOrganisationVegetale.java) | Automate cellulaire végétal | Compétition cyclique A→B→C→A avec facilitation (Swing) |

## Exécution

```bash
javac ecosysteme/EcosystemeSimple.java
java -cp . ecosysteme.EcosystemeSimple
```

L'`EcosystemeSimple` intègre un bouton "Perturbation 90%" qui illustre la résilience face à une diminution brutale de la population d'herbivores.
