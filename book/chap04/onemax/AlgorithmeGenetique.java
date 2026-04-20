package onemax;

import java.util.Arrays;
import java.util.Random;

public class AlgorithmeGenetique {

    private static final int TAILLE_POPULATION = 100;
    private static final int TAILLE_CHROMOSOME = 50;
    private static final double TAUX_MUTATION = 0.01;
    private static final double TAUX_CROISEMENT = 0.7;
    private static final int MAX_GENERATIONS = 100;

    private Random random = new Random();
    private boolean[][] population;
    private double[] fitness;

    public AlgorithmeGenetique() {
        population = new boolean[TAILLE_POPULATION][TAILLE_CHROMOSOME];
        fitness = new double[TAILLE_POPULATION];

        for (int i = 0; i < TAILLE_POPULATION; i++) {
            for (int j = 0; j < TAILLE_CHROMOSOME; j++) {
                population[i][j] = random.nextBoolean();
            }
        }
    }

    private double evaluerFitness(boolean[] chromosome) {
        int compteur = 0;
        for (boolean gene : chromosome) {
            if (gene) compteur++;
        }
        return compteur;
    }

    private void evaluerPopulation() {
        for (int i = 0; i < TAILLE_POPULATION; i++) {
            fitness[i] = evaluerFitness(population[i]);
        }
    }

    private int selection() {
        int indice1 = random.nextInt(TAILLE_POPULATION);
        int indice2 = random.nextInt(TAILLE_POPULATION);
        return (fitness[indice1] > fitness[indice2]) ? indice1 : indice2;
    }

    private boolean[][] croisement(boolean[] parent1, boolean[] parent2) {
        boolean[][] enfants = new boolean[2][TAILLE_CHROMOSOME];

        if (random.nextDouble() > TAUX_CROISEMENT) {
            enfants[0] = Arrays.copyOf(parent1, TAILLE_CHROMOSOME);
            enfants[1] = Arrays.copyOf(parent2, TAILLE_CHROMOSOME);
            return enfants;
        }

        int pointCroisement = random.nextInt(TAILLE_CHROMOSOME);

        for (int i = 0; i < TAILLE_CHROMOSOME; i++) {
            if (i < pointCroisement) {
                enfants[0][i] = parent1[i];
                enfants[1][i] = parent2[i];
            } else {
                enfants[0][i] = parent2[i];
                enfants[1][i] = parent1[i];
            }
        }

        return enfants;
    }

    private void mutation(boolean[] chromosome) {
        for (int i = 0; i < TAILLE_CHROMOSOME; i++) {
            if (random.nextDouble() < TAUX_MUTATION) {
                chromosome[i] = !chromosome[i];
            }
        }
    }

    public void executer() {
        evaluerPopulation();

        for (int generation = 0; generation < MAX_GENERATIONS; generation++) {
            boolean[][] nouvellePopulation = new boolean[TAILLE_POPULATION][TAILLE_CHROMOSOME];

            int indiceMeilleur = trouverMeilleurIndividu();
            nouvellePopulation[0] = Arrays.copyOf(population[indiceMeilleur], TAILLE_CHROMOSOME);

            for (int i = 1; i < TAILLE_POPULATION; i += 2) {
                int indiceParent1 = selection();
                int indiceParent2 = selection();

                boolean[][] enfants = croisement(population[indiceParent1], population[indiceParent2]);

                mutation(enfants[0]);
                mutation(enfants[1]);

                nouvellePopulation[i] = enfants[0];
                if (i + 1 < TAILLE_POPULATION) {
                    nouvellePopulation[i + 1] = enfants[1];
                }
            }

            population = nouvellePopulation;
            evaluerPopulation();

            double fitnessMax = fitness[trouverMeilleurIndividu()];
            System.out.println("Génération " + generation + ": Fitness max = " + fitnessMax);

            if (fitnessMax == TAILLE_CHROMOSOME) {
                System.out.println("Solution optimale trouvée à la génération " + generation);
                break;
            }
        }
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

    public void afficherMeilleurIndividu() {
        int indice = trouverMeilleurIndividu();
        System.out.println("Meilleur individu (fitness = " + fitness[indice] + "):");
        for (boolean gene : population[indice]) {
            System.out.print(gene ? "1" : "0");
        }
        System.out.println();
    }

    public static void main(String[] args) {
        AlgorithmeGenetique ag = new AlgorithmeGenetique();
        System.out.println("Début de l'algorithme génétique");
        ag.executer();
        ag.afficherMeilleurIndividu();
    }
}
