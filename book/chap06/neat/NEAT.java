package neat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Implémentation simplifiée de NEAT (NeuroEvolution of Augmenting Topologies).
 * Contient les classes Gene*, Genome, ReseauNeuronal et l'algorithme évolutionnaire.
 */
public class NEAT {

    static class GeneConnexion {
        int entree, sortie;
        double poids;
        boolean active;
        int innovation;

        GeneConnexion(int entree, int sortie, double poids, boolean active, int innovation) {
            this.entree = entree;
            this.sortie = sortie;
            this.poids = poids;
            this.active = active;
            this.innovation = innovation;
        }

        @Override
        public GeneConnexion clone() {
            return new GeneConnexion(entree, sortie, poids, active, innovation);
        }
    }

    static class GeneNoeud {
        int id;
        String type;

        GeneNoeud(int id, String type) {
            this.id = id;
            this.type = type;
        }

        @Override
        public GeneNoeud clone() {
            return new GeneNoeud(id, type);
        }
    }

    static class Genome {
        List<GeneNoeud> noeuds = new ArrayList<>();
        List<GeneConnexion> connexions = new ArrayList<>();
        double fitness = 0;
        int espece = 0;

        ReseauNeuronal creerReseau() {
            return new ReseauNeuronal();
        }

        double distanceGenetique(Genome autre) {
            double c1 = 1.0, c2 = 1.0, c3 = 0.4;
            int nbDisjoints = 0, nbExces = 0, nbCommuns = 0;
            double diffPoids = 0;

            int maxInnov1 = connexions.isEmpty() ? 0 :
                            connexions.stream().mapToInt(g -> g.innovation).max().getAsInt();
            int maxInnov2 = autre.connexions.isEmpty() ? 0 :
                            autre.connexions.stream().mapToInt(g -> g.innovation).max().getAsInt();

            Map<Integer, GeneConnexion> map1 = new HashMap<>();
            for (GeneConnexion gene : connexions) map1.put(gene.innovation, gene);

            Map<Integer, GeneConnexion> map2 = new HashMap<>();
            for (GeneConnexion gene : autre.connexions) map2.put(gene.innovation, gene);

            for (int i = 1; i <= Math.max(maxInnov1, maxInnov2); i++) {
                GeneConnexion gene1 = map1.get(i);
                GeneConnexion gene2 = map2.get(i);

                if (gene1 == null && gene2 == null) continue;
                else if (gene1 == null) {
                    if (i > maxInnov1) nbExces++;
                    else nbDisjoints++;
                } else if (gene2 == null) {
                    if (i > maxInnov2) nbExces++;
                    else nbDisjoints++;
                } else {
                    nbCommuns++;
                    diffPoids += Math.abs(gene1.poids - gene2.poids);
                }
            }

            int n = Math.max(connexions.size(), autre.connexions.size());
            if (n < 20) n = 1;

            double distance = (c1 * nbDisjoints / n) + (c2 * nbExces / n);
            if (nbCommuns > 0) distance += c3 * diffPoids / nbCommuns;

            return distance;
        }

        Genome croiser(Genome autre) {
            Genome plusFit = this.fitness >= autre.fitness ? this : autre;
            Genome moinsFit = this.fitness >= autre.fitness ? autre : this;

            Genome enfant = new Genome();
            for (GeneNoeud noeud : plusFit.noeuds) enfant.noeuds.add(noeud.clone());

            Map<Integer, GeneConnexion> mapPlusFit = new HashMap<>();
            for (GeneConnexion gene : plusFit.connexions) mapPlusFit.put(gene.innovation, gene);

            Map<Integer, GeneConnexion> mapMoinsFit = new HashMap<>();
            for (GeneConnexion gene : moinsFit.connexions) mapMoinsFit.put(gene.innovation, gene);

            int maxInnov = Math.max(
                plusFit.connexions.isEmpty() ? 0 :
                    plusFit.connexions.stream().mapToInt(g -> g.innovation).max().getAsInt(),
                moinsFit.connexions.isEmpty() ? 0 :
                    moinsFit.connexions.stream().mapToInt(g -> g.innovation).max().getAsInt()
            );

            for (int i = 1; i <= maxInnov; i++) {
                GeneConnexion gene1 = mapPlusFit.get(i);
                GeneConnexion gene2 = mapMoinsFit.get(i);

                if (gene1 != null && gene2 != null) {
                    enfant.connexions.add(Math.random() < 0.5 ? gene1.clone() : gene2.clone());
                } else if (gene1 != null) {
                    enfant.connexions.add(gene1.clone());
                }
            }

            return enfant;
        }

        void muter(double tauxMutationPoids, double tauxAjoutConnexion, double tauxAjoutNoeud) {
            Random random = new Random();

            for (GeneConnexion gene : connexions) {
                if (random.nextDouble() < tauxMutationPoids) {
                    if (random.nextDouble() < 0.9) {
                        gene.poids += random.nextGaussian() * 0.1;
                    } else {
                        gene.poids = random.nextGaussian();
                    }
                }
            }
            // Les mutations structurelles (ajout de connexion, ajout de nœud)
            // sont laissées à une version complète.
        }
    }

    static class ReseauNeuronal {
        double[] calculerSortie(double[] entrees) {
            return new double[1];
        }
    }

    private List<Genome> population;
    private int taillePopulation;
    private double seuilSpeciation;
    private int compteurInnovation;

    public NEAT(int taillePopulation, int nbEntrees, int nbSorties) {
        this.taillePopulation = taillePopulation;
        this.seuilSpeciation = 3.0;
        this.compteurInnovation = 0;

        population = new ArrayList<>();
        for (int i = 0; i < taillePopulation; i++) {
            Genome genome = new Genome();

            for (int j = 0; j < nbEntrees; j++) {
                genome.noeuds.add(new GeneNoeud(j, "entrée"));
            }

            for (int j = 0; j < nbSorties; j++) {
                genome.noeuds.add(new GeneNoeud(nbEntrees + j, "sortie"));
            }

            for (int in = 0; in < nbEntrees; in++) {
                for (int out = 0; out < nbSorties; out++) {
                    genome.connexions.add(new GeneConnexion(
                        in, nbEntrees + out,
                        Math.random() * 4 - 2,
                        true, ++compteurInnovation
                    ));
                }
            }

            population.add(genome);
        }
    }

    public void evaluer(FonctionFitness fonctionFitness) {
        for (Genome genome : population) {
            ReseauNeuronal reseau = genome.creerReseau();
            genome.fitness = fonctionFitness.evaluer(reseau);
        }
    }

    public void speciation() {
        for (Genome genome : population) genome.espece = 0;

        List<Genome> representants = new ArrayList<>();
        int especeActuelle = 1;

        for (Genome genome : population) {
            if (genome.espece == 0) {
                boolean assigne = false;

                for (Genome representant : representants) {
                    if (genome.distanceGenetique(representant) < seuilSpeciation) {
                        genome.espece = representant.espece;
                        assigne = true;
                        break;
                    }
                }

                if (!assigne) {
                    genome.espece = especeActuelle++;
                    representants.add(genome);
                }
            }
        }
    }

    public void nouvelleGeneration() {
        speciation();

        Map<Integer, Integer> tailleEspeces = new HashMap<>();
        for (Genome genome : population) {
            tailleEspeces.put(genome.espece, tailleEspeces.getOrDefault(genome.espece, 0) + 1);
        }

        for (Genome genome : population) {
            genome.fitness /= tailleEspeces.get(genome.espece);
        }

        List<Genome> nouvellePopulation = new ArrayList<>();

        Map<Integer, Genome> meilleurs = new HashMap<>();
        for (Genome genome : population) {
            int espece = genome.espece;
            if (!meilleurs.containsKey(espece) || genome.fitness > meilleurs.get(espece).fitness) {
                meilleurs.put(espece, genome);
            }
        }

        for (Genome meilleur : meilleurs.values()) nouvellePopulation.add(meilleur);

        while (nouvellePopulation.size() < taillePopulation) {
            Genome parent1 = selectionRoulette();
            Genome parent2 = selectionRoulette();
            Genome enfant = parent1.croiser(parent2);
            enfant.muter(0.8, 0.05, 0.03);
            nouvellePopulation.add(enfant);
        }

        population = nouvellePopulation;
    }

    private Genome selectionRoulette() {
        double sommeFitness = population.stream().mapToDouble(g -> Math.max(g.fitness, 0.0001)).sum();
        double valeur = Math.random() * sommeFitness;

        double cumul = 0;
        for (Genome genome : population) {
            cumul += Math.max(genome.fitness, 0.0001);
            if (cumul >= valeur) return genome;
        }

        return population.get(population.size() - 1);
    }

    public interface FonctionFitness {
        double evaluer(ReseauNeuronal reseau);
    }

    public static void main(String[] args) {
        NEAT neat = new NEAT(50, 3, 2);
        System.out.println("Population NEAT initialisée : " + neat.population.size() + " génomes");
        neat.evaluer(reseau -> Math.random());
        neat.speciation();
        System.out.println("Spéciation terminée");
    }
}
