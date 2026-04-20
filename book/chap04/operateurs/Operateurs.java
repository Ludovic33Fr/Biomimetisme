package operateurs;

import java.util.Arrays;
import java.util.Random;

/**
 * Catalogue des opérateurs génétiques utilisables dans un algorithme évolutionnaire.
 * Regroupe trois méthodes de sélection, trois méthodes de croisement et deux méthodes
 * de mutation. Chaque opérateur peut être utilisé indépendamment.
 */
public class Operateurs {

    private final Random random = new Random();
    private double tauxMutation = 0.01;

    // === SÉLECTION ===

    public int selectionRoulette(double[] fitness) {
        double somme = 0;
        for (double f : fitness) {
            somme += f;
        }

        double valeur = random.nextDouble() * somme;

        double cumul = 0;
        for (int i = 0; i < fitness.length; i++) {
            cumul += fitness[i];
            if (cumul >= valeur) {
                return i;
            }
        }

        return fitness.length - 1;
    }

    public int selectionRang(double[] fitness) {
        Integer[] indices = new Integer[fitness.length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        Arrays.sort(indices, (a, b) -> Double.compare(fitness[b], fitness[a]));

        double sommeRangs = (fitness.length * (fitness.length + 1)) / 2.0;
        double valeur = random.nextDouble() * sommeRangs;

        double cumul = 0;
        for (int i = 0; i < indices.length; i++) {
            cumul += (fitness.length - i);
            if (cumul >= valeur) {
                return indices[i];
            }
        }

        return indices[indices.length - 1];
    }

    public int selectionTournoi(double[] fitness, int tailleTournoi) {
        int meilleur = random.nextInt(fitness.length);

        for (int i = 1; i < tailleTournoi; i++) {
            int candidat = random.nextInt(fitness.length);
            if (fitness[candidat] > fitness[meilleur]) {
                meilleur = candidat;
            }
        }

        return meilleur;
    }

    // === CROISEMENT ===

    public int[][] croisementUnPoint(int[] parent1, int[] parent2) {
        int[][] enfants = new int[2][parent1.length];

        int point = random.nextInt(parent1.length);

        for (int i = 0; i < parent1.length; i++) {
            if (i < point) {
                enfants[0][i] = parent1[i];
                enfants[1][i] = parent2[i];
            } else {
                enfants[0][i] = parent2[i];
                enfants[1][i] = parent1[i];
            }
        }

        return enfants;
    }

    public int[][] croisementDeuxPoints(int[] parent1, int[] parent2) {
        int[][] enfants = new int[2][parent1.length];

        int point1 = random.nextInt(parent1.length);
        int point2 = random.nextInt(parent1.length);

        if (point1 > point2) {
            int temp = point1;
            point1 = point2;
            point2 = temp;
        }

        for (int i = 0; i < parent1.length; i++) {
            if (i < point1 || i >= point2) {
                enfants[0][i] = parent1[i];
                enfants[1][i] = parent2[i];
            } else {
                enfants[0][i] = parent2[i];
                enfants[1][i] = parent1[i];
            }
        }

        return enfants;
    }

    public int[][] croisementUniforme(int[] parent1, int[] parent2, double probabilite) {
        int[][] enfants = new int[2][parent1.length];

        for (int i = 0; i < parent1.length; i++) {
            if (random.nextDouble() < probabilite) {
                enfants[0][i] = parent2[i];
                enfants[1][i] = parent1[i];
            } else {
                enfants[0][i] = parent1[i];
                enfants[1][i] = parent2[i];
            }
        }

        return enfants;
    }

    // === MUTATION ===

    public void mutationBinaire(boolean[] chromosome, double tauxMutation) {
        for (int i = 0; i < chromosome.length; i++) {
            if (random.nextDouble() < tauxMutation) {
                chromosome[i] = !chromosome[i];
            }
        }
    }

    public void mutationGaussienne(double[] chromosome, double sigma) {
        for (int i = 0; i < chromosome.length; i++) {
            if (random.nextDouble() < tauxMutation) {
                chromosome[i] += sigma * random.nextGaussian();
            }
        }
    }
}
