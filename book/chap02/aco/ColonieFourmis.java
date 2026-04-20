package aco;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ColonieFourmis {

    private static final int NB_FOURMIS = 20;
    private static final int NB_ITERATIONS = 100;
    private static final int NB_VILLES = 20;
    private static final double ALPHA = 1.0;
    private static final double BETA = 2.0;
    private static final double RHO = 0.5;
    private static final double Q = 100.0;
    private static final double TAILLE_MONDE = 100.0;

    private Random random = new Random();
    private double[][] distances;
    private double[][] pheromones;
    private double[][] heuristique;

    private class Fourmi {
        private int[] parcours;
        private boolean[] villesVisitees;
        private int villeActuelle;
        private int villeDepart;
        private double distanceTotale;

        public Fourmi() {
            parcours = new int[NB_VILLES];
            villesVisitees = new boolean[NB_VILLES];
            villeDepart = random.nextInt(NB_VILLES);
            villeActuelle = villeDepart;

            parcours[0] = villeDepart;
            villesVisitees[villeDepart] = true;

            distanceTotale = 0.0;
        }

        public void construireSolution() {
            for (int i = 1; i < NB_VILLES; i++) {
                int prochaineVille = choisirProchaineVille();
                parcours[i] = prochaineVille;
                villesVisitees[prochaineVille] = true;
                distanceTotale += distances[villeActuelle][prochaineVille];
                villeActuelle = prochaineVille;
            }
            distanceTotale += distances[villeActuelle][villeDepart];
        }

        private int choisirProchaineVille() {
            double[] probabilites = new double[NB_VILLES];
            double somme = 0.0;

            for (int ville = 0; ville < NB_VILLES; ville++) {
                if (!villesVisitees[ville]) {
                    probabilites[ville] = Math.pow(pheromones[villeActuelle][ville], ALPHA) *
                                         Math.pow(heuristique[villeActuelle][ville], BETA);
                    somme += probabilites[ville];
                }
            }

            for (int ville = 0; ville < NB_VILLES; ville++) {
                if (!villesVisitees[ville]) {
                    probabilites[ville] /= somme;
                }
            }

            double r = random.nextDouble();
            double cumulatif = 0.0;

            for (int ville = 0; ville < NB_VILLES; ville++) {
                if (!villesVisitees[ville]) {
                    cumulatif += probabilites[ville];
                    if (r <= cumulatif) return ville;
                }
            }

            for (int ville = 0; ville < NB_VILLES; ville++) {
                if (!villesVisitees[ville]) return ville;
            }

            return -1;
        }

        public void deposerPheromones() {
            double quantite = Q / distanceTotale;

            for (int i = 0; i < NB_VILLES - 1; i++) {
                int ville1 = parcours[i];
                int ville2 = parcours[i + 1];
                pheromones[ville1][ville2] += quantite;
                pheromones[ville2][ville1] += quantite;
            }

            int derniere = parcours[NB_VILLES - 1];
            pheromones[derniere][villeDepart] += quantite;
            pheromones[villeDepart][derniere] += quantite;
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

        pheromones = new double[NB_VILLES][NB_VILLES];
        heuristique = new double[NB_VILLES][NB_VILLES];

        for (int i = 0; i < NB_VILLES; i++) {
            for (int j = 0; j < NB_VILLES; j++) {
                if (i != j) {
                    pheromones[i][j] = 0.1;
                    heuristique[i][j] = 1.0 / distances[i][j];
                }
            }
        }

        System.out.println("Problème initialisé avec " + NB_VILLES + " villes");
    }

    public void executer() {
        double meilleurDistance = Double.POSITIVE_INFINITY;
        int[] meilleurParcours = new int[NB_VILLES];

        for (int iteration = 1; iteration <= NB_ITERATIONS; iteration++) {
            List<Fourmi> fourmis = new ArrayList<>();
            for (int i = 0; i < NB_FOURMIS; i++) {
                fourmis.add(new Fourmi());
            }

            for (Fourmi fourmi : fourmis) {
                fourmi.construireSolution();

                if (fourmi.distanceTotale < meilleurDistance) {
                    meilleurDistance = fourmi.distanceTotale;
                    System.arraycopy(fourmi.parcours, 0, meilleurParcours, 0, NB_VILLES);

                    System.out.printf("Itération %3d: Nouvelle meilleure distance = %.2f\n",
                                     iteration, meilleurDistance);
                }
            }

            for (int i = 0; i < NB_VILLES; i++) {
                for (int j = 0; j < NB_VILLES; j++) {
                    pheromones[i][j] *= (1 - RHO);
                }
            }

            for (Fourmi fourmi : fourmis) {
                fourmi.deposerPheromones();
            }

            if (iteration % 10 == 0 && iteration != NB_ITERATIONS) {
                System.out.printf("Itération %3d: Meilleure distance = %.2f\n",
                                 iteration, meilleurDistance);
            }
        }

        System.out.println("\nMeilleur parcours final:");
        for (int i = 0; i < NB_VILLES; i++) {
            System.out.print(meilleurParcours[i] + " -> ");
        }
        System.out.println(meilleurParcours[0]);
        System.out.printf("Distance totale: %.2f\n", meilleurDistance);
    }

    public static void main(String[] args) {
        ColonieFourmis aco = new ColonieFourmis();
        aco.initialiserProbleme();
        aco.executer();
    }
}
