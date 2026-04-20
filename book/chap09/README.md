# Chapitre 9 - Durabilité et éco-conception

Code d'illustration du Chapitre 9 du livre **Biomimétisme Informatique**.

## Contenu

| Dossier | Sujet | Problème |
|---------|-------|----------|
| [`metabolisme/`](metabolisme/MetabolicEnergyManager.java) | États énergétiques bio-inspirés | Hibernation/LowPower/Standard/HighPerf selon batterie et charge |
| [`ecosysteme/`](ecosysteme/EcosystemInspiredDistributedSystem.java) | Système distribué type écosystème | Niches énergétiques, dynamique de populations de nœuds |
| [`empreinte/`](empreinte/EnergyFootprintMonitor.java) | Monitoring empreinte énergétique | Mesure CPU/mémoire/IO, conversion en kWh et gCO2e |
| [`ga/`](ga/GeneticEnergyOptimizer.java) | AG pour allocation VMs→PMs | Minimiser la consommation d'un data center |
| [`adaptive/`](adaptive/AdaptiveBioInspiredService.java) | Service QoS homéostatique | Ajustement qualité selon batterie et charge |
| [`aco/`](aco/AntColonyVMOptimizer.java) | ACO pour allocation de VMs | Variante colonies de fourmis pour data center |
| [`mobile/`](mobile/BioInspiredAdaptationManager.java) | Adaptation app mobile | GPS/rendu/prefetch adaptatifs selon état du système |

## Exécution

```bash
javac metabolisme/MetabolicEnergyManager.java
java -cp . metabolisme.MetabolicEnergyManager
```
