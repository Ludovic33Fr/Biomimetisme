package tsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class AlgorithmeGenetiqueTSP {

    private static final int TAILLE_POPULATION = 50;
    private static final double TAUX_MUTATION = 0.02;
    private static final double TAUX_CROISEMENT = 0.8;
    private static final int MAX_GENERATIONS = 500;

    private Random random = new Random();
    private int nombreVilles;
    private double[][] distances;
    private int[][] population;
    private double[] fitness;

    public AlgorithmeGenetiqueTSP(double[][] distances) {
        this.distances = distances;
        this.nombreVilles = distances.length;
        this.population = new int[TAILLE_POPULATION][nombreVilles];
        this.fitness = new double[TAILLE_POPULATION];

        for (int i = 0; i < TAILLE_POPULATION; i++) {
            population[i] = genererPermutationAleatoire();
        }
    }

    private int[] genererPermutationAleatoire() {
        List<Integer> villes = new ArrayList<>();
        for (int i = 0; i < nombreVilles; i++) {
            villes.add(i);
        }
        Collections.shuffle(villes, random);

        int[] permutation = new int[nombreVilles];
        for (int i = 0; i < nombreVilles; i++) {
            permutation[i] = villes.get(i);
        }
        return permutation;
    }

    private double calculerLongueurCircuit(int[] circuit) {
        double longueur = 0;
        for (int i = 0; i < nombreVilles - 1; i++) {
            longueur += distances[circuit[i]][circuit[i+1]];
        }
        longueur += distances[circuit[nombreVilles-1]][circuit[0]];
        return longueur;
    }

    private void evaluerPopulation() {
        for (int i = 0; i < TAILLE_POPULATION; i++) {
            double longueur = calculerLongueurCircuit(population[i]);
            fitness[i] = 1.0 / longueur;
        }
    }

    private int selection() {
        int tailleTournoi = 3;
        int meilleur = random.nextInt(TAILLE_POPULATION);

        for (int i = 1; i < tailleTournoi; i++) {
            int candidat = random.nextInt(TAILLE_POPULATION);
            if (fitness[candidat] > fitness[meilleur]) {
                meilleur = candidat;
            }
        }

        return meilleur;
    }

    private int[] croisementOX(int[] parent1, int[] parent2) {
        int[] enfant = new int[nombreVilles];
        Arrays.fill(enfant, -1);

        if (random.nextDouble() > TAUX_CROISEMENT) {
            return Arrays.copyOf(parent1, nombreVilles);
        }

        int point1 = random.nextInt(nombreVilles);
        int point2 = random.nextInt(nombreVilles);

        if (point1 > point2) {
            int temp = point1;
            point1 = point2;
            point2 = temp;
        }

        for (int i = point1; i <= point2; i++) {
            enfant[i] = parent1[i];
        }

        int position = (point2 + 1) % nombreVilles;
        for (int i = 0; i < nombreVilles; i++) {
            int ville = parent2[(point2 + 1 + i) % nombreVilles];

            boolean dejaPresent = false;
            for (int j = 0; j < nombreVilles; j++) {
                if (enfant[j] == ville) {
                    dejaPresent = true;
                    break;
                }
            }

            if (!dejaPresent) {
                while (enfant[position] != -1) {
                    position = (position + 1) % nombreVilles;
                }
                enfant[position] = ville;
                position = (position + 1) % nombreVilles;
            }
        }

        return enfant;
    }

    private void mutation(int[] circuit) {
        for (int i = 0; i < nombreVilles; i++) {
            if (random.nextDouble() < TAUX_MUTATION) {
                int j = random.nextInt(nombreVilles);
                int temp = circuit[i];
                circuit[i] = circuit[j];
                circuit[j] = temp;
            }
        }
    }

    public void executer() {
        evaluerPopulation();

        System.out.println("Début de l'algorithme génétique pour le TSP");
        System.out.println("Meilleure distance initiale: " + (1.0 / fitness[trouverMeilleurIndividu()]));

        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            int[][] nouvellePopulation = new int[TAILLE_POPULATION][nombreVilles];

            int indiceMeilleur = trouverMeilleurIndividu();
            nouvellePopulation[0] = Arrays.copyOf(population[indiceMeilleur], nombreVilles);

            for (int i = 1; i < TAILLE_POPULATION; i++) {
                int indiceParent1 = selection();
                int indiceParent2 = selection();

                nouvellePopulation[i] = croisementOX(population[indiceParent1], population[indiceParent2]);

                mutation(nouvellePopulation[i]);
            }

            population = nouvellePopulation;
            evaluerPopulation();

            if (generation % 50 == 0) {
                double meilleureFitness = fitness[trouverMeilleurIndividu()];
                System.out.println("Génération " + generation + ": Meilleure distance = " + (1.0 / meilleureFitness));
            }
        }

        int indiceMeilleur = trouverMeilleurIndividu();
        System.out.println("\nRésultat final:");
        System.out.println("Meilleure distance = " + (1.0 / fitness[indiceMeilleur]));
        System.out.print("Circuit optimal = [");
        for (int i = 0; i < nombreVilles; i++) {
            System.out.print(population[indiceMeilleur][i]);
            if (i < nombreVilles - 1) System.out.print(" -> ");
        }
        System.out.println(" -> " + population[indiceMeilleur][0] + "]");
    }

    private int trouverMeilleurIndividu() {
        int indice = 0;
        for (int i = 1; i < TAILLE_POPULATION; i++) {
            if (fitness[i] > fitness[indice]) {
                indice = i;
            }
        }
        return indice;
    }

    public static void main(String[] args) {
        int n = 10;
        double[][] distances = new double[n][n];

        Random rand = new Random();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                distances[i][j] = 10 + rand.nextDouble() * 90;
                distances[j][i] = distances[i][j];
            }
        }

        AlgorithmeGenetiqueTSP tsp = new AlgorithmeGenetiqueTSP(distances);
        tsp.executer();
    }
}
