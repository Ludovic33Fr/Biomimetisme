package tsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class AlgorithmeGenetiqueTSP {

    private static final int TAILLE_POPULATION = 100;
    private static final double TAUX_CROISEMENT = 0.8;
    private static final double TAUX_MUTATION = 0.1;
    private static final int NB_GENERATIONS = 500;
    private static final int NB_VILLES = 20;
    private static final double TAILLE_MONDE = 100.0;

    private Random random = new Random();
    private double[][] distances;

    private class Individu {
        private int[] parcours;
        private double fitness;

        public Individu() {
            parcours = new int[NB_VILLES];
            for (int i = 0; i < NB_VILLES; i++) {
                parcours[i] = i;
            }

            for (int i = 1; i < NB_VILLES; i++) {
                int j = 1 + random.nextInt(NB_VILLES - 1);
                int temp = parcours[i];
                parcours[i] = parcours[j];
                parcours[j] = temp;
            }

            calculerFitness();
        }

        public Individu(int[] parcours) {
            this.parcours = parcours;
            calculerFitness();
        }

        private void calculerFitness() {
            double distanceTotale = 0;
            for (int i = 0; i < NB_VILLES - 1; i++) {
                distanceTotale += distances[parcours[i]][parcours[i + 1]];
            }
            distanceTotale += distances[parcours[NB_VILLES - 1]][parcours[0]];
            fitness = 1.0 / distanceTotale;
        }

        public Individu croiser(Individu autre) {
            if (random.nextDouble() > TAUX_CROISEMENT) {
                return new Individu(parcours.clone());
            }

            int[] enfantParcours = new int[NB_VILLES];
            Arrays.fill(enfantParcours, -1);

            int point1 = 1 + random.nextInt(NB_VILLES - 2);
            int point2 = point1 + 1 + random.nextInt(NB_VILLES - point1 - 1);

            for (int i = point1; i <= point2; i++) {
                enfantParcours[i] = this.parcours[i];
            }

            int j = (point2 + 1) % NB_VILLES;
            for (int i = 0; i < NB_VILLES; i++) {
                int villeCandidate = autre.parcours[(point2 + 1 + i) % NB_VILLES];

                boolean dejaPresente = false;
                for (int k = 0; k < NB_VILLES; k++) {
                    if (enfantParcours[k] == villeCandidate) {
                        dejaPresente = true;
                        break;
                    }
                }

                if (!dejaPresente) {
                    enfantParcours[j] = villeCandidate;
                    j = (j + 1) % NB_VILLES;
                    if (j == point1) {
                        j = (point2 + 1) % NB_VILLES;
                    }
                }
            }

            return new Individu(enfantParcours);
        }

        public void muter() {
            if (random.nextDouble() < TAUX_MUTATION) {
                int pos1 = 1 + random.nextInt(NB_VILLES - 1);
                int pos2 = 1 + random.nextInt(NB_VILLES - 1);

                if (pos1 > pos2) { int t = pos1; pos1 = pos2; pos2 = t; }

                while (pos1 < pos2) {
                    int temp = parcours[pos1];
                    parcours[pos1] = parcours[pos2];
                    parcours[pos2] = temp;
                    pos1++;
                    pos2--;
                }

                calculerFitness();
            }
        }

        public double getDistanceTotale() {
            return 1.0 / fitness;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Parcours: ");
            for (int ville : parcours) sb.append(ville).append(" -> ");
            sb.append(parcours[0]);
            sb.append(" (Distance: ").append(String.format("%.2f", getDistanceTotale())).append(")");
            return sb.toString();
        }
    }

    public void initialiserProbleme() {
        double[][] coordonnees = new double[NB_VILLES][2];
        for (int i = 0; i < NB_VILLES; i++) {
            coordonnees[i][0] = random.nextDouble() * TAILLE_MONDE;
            coordonnees[i][1] = random.nextDouble() * TAILLE_MONDE;
        }

        distances = new double[NB_VILLES][NB_VILLES];
        for (int i = 0; i < NB_VILLES; i++) {
            for (int j = 0; j < NB_VILLES; j++) {
                if (i == j) {
                    distances[i][j] = 0;
                } else {
                    double dx = coordonnees[i][0] - coordonnees[j][0];
                    double dy = coordonnees[i][1] - coordonnees[j][1];
                    distances[i][j] = Math.sqrt(dx * dx + dy * dy);
                }
            }
        }

        System.out.println("Problème initialisé avec " + NB_VILLES + " villes");
    }

    public void executer() {
        List<Individu> population = new ArrayList<>();
        for (int i = 0; i < TAILLE_POPULATION; i++) {
            population.add(new Individu());
        }

        Individu meilleurGlobal = trouverMeilleur(population);
        System.out.println("Meilleur initial: " + meilleurGlobal);

        for (int generation = 1; generation <= NB_GENERATIONS; generation++) {
            List<Individu> nouvellePopulation = new ArrayList<>();
            nouvellePopulation.add(new Individu(meilleurGlobal.parcours.clone()));

            while (nouvellePopulation.size() < TAILLE_POPULATION) {
                Individu parent1 = selectionTournoi(population);
                Individu parent2 = selectionTournoi(population);
                Individu enfant = parent1.croiser(parent2);
                enfant.muter();
                nouvellePopulation.add(enfant);
            }

            population = nouvellePopulation;

            Individu meilleurCourant = trouverMeilleur(population);
            if (meilleurCourant.fitness > meilleurGlobal.fitness) {
                meilleurGlobal = new Individu(meilleurCourant.parcours.clone());
            }

            if (generation % 50 == 0 || generation == NB_GENERATIONS) {
                System.out.printf("Génération %3d: Meilleure distance = %.2f\n",
                                 generation, meilleurGlobal.getDistanceTotale());
            }
        }

        System.out.println("\nMeilleur parcours final: " + meilleurGlobal);
    }

    private Individu selectionTournoi(List<Individu> population) {
        Individu meilleur = null;
        for (int i = 0; i < 5; i++) {
            Individu candidat = population.get(random.nextInt(population.size()));
            if (meilleur == null || candidat.fitness > meilleur.fitness) {
                meilleur = candidat;
            }
        }
        return meilleur;
    }

    private Individu trouverMeilleur(List<Individu> population) {
        Individu meilleur = population.get(0);
        for (Individu individu : population) {
            if (individu.fitness > meilleur.fitness) meilleur = individu;
        }
        return meilleur;
    }

    public static void main(String[] args) {
        AlgorithmeGenetiqueTSP ag = new AlgorithmeGenetiqueTSP();
        ag.initialiserProbleme();
        ag.executer();
    }
}
