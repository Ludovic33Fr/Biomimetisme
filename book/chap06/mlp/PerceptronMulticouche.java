package mlp;

import java.util.Random;

public class PerceptronMulticouche {
    private int[] tailleCouches;
    private double[][] sorties;
    private double[][] biais;
    private double[][][] poids;
    private double[][] deltas;

    private Random random = new Random();

    public PerceptronMulticouche(int[] tailleCouches) {
        this.tailleCouches = tailleCouches;
        int nbCouches = tailleCouches.length;

        sorties = new double[nbCouches][];
        biais = new double[nbCouches][];
        deltas = new double[nbCouches][];
        poids = new double[nbCouches - 1][][];

        for (int i = 0; i < nbCouches; i++) {
            sorties[i] = new double[tailleCouches[i]];
            deltas[i] = new double[tailleCouches[i]];

            if (i > 0) {
                biais[i] = new double[tailleCouches[i]];
                for (int j = 0; j < tailleCouches[i]; j++) {
                    biais[i][j] = random.nextGaussian() * 0.1;
                }

                poids[i - 1] = new double[tailleCouches[i - 1]][tailleCouches[i]];
                for (int j = 0; j < tailleCouches[i - 1]; j++) {
                    for (int k = 0; k < tailleCouches[i]; k++) {
                        double limite = Math.sqrt(6.0 / (tailleCouches[i - 1] + tailleCouches[i]));
                        poids[i - 1][j][k] = random.nextDouble() * 2 * limite - limite;
                    }
                }
            }
        }
    }

    private double sigmoide(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    public double[] propagationAvant(double[] entree) {
        System.arraycopy(entree, 0, sorties[0], 0, entree.length);

        for (int i = 1; i < tailleCouches.length; i++) {
            for (int j = 0; j < tailleCouches[i]; j++) {
                double somme = biais[i][j];
                for (int k = 0; k < tailleCouches[i - 1]; k++) {
                    somme += sorties[i - 1][k] * poids[i - 1][k][j];
                }
                sorties[i][j] = sigmoide(somme);
            }
        }

        return sorties[sorties.length - 1];
    }

    public double retropropagation(double[] cible, double tauxApprentissage) {
        int nbCouches = tailleCouches.length;
        double erreur = 0;

        for (int i = 0; i < tailleCouches[nbCouches - 1]; i++) {
            double sortie = sorties[nbCouches - 1][i];
            double erreurNeurone = cible[i] - sortie;
            deltas[nbCouches - 1][i] = erreurNeurone * sortie * (1 - sortie);
            erreur += erreurNeurone * erreurNeurone;
        }
        erreur /= tailleCouches[nbCouches - 1];

        for (int i = nbCouches - 2; i > 0; i--) {
            for (int j = 0; j < tailleCouches[i]; j++) {
                double somme = 0;
                for (int k = 0; k < tailleCouches[i + 1]; k++) {
                    somme += deltas[i + 1][k] * poids[i][j][k];
                }
                deltas[i][j] = somme * sorties[i][j] * (1 - sorties[i][j]);
            }
        }

        for (int i = nbCouches - 1; i > 0; i--) {
            for (int j = 0; j < tailleCouches[i]; j++) {
                biais[i][j] += tauxApprentissage * deltas[i][j];
                for (int k = 0; k < tailleCouches[i - 1]; k++) {
                    poids[i - 1][k][j] += tauxApprentissage * deltas[i][j] * sorties[i - 1][k];
                }
            }
        }

        return erreur;
    }

    public void entrainer(double[][] entrees, double[][] cibles, int epochs, double tauxApprentissage) {
        for (int epoch = 0; epoch < epochs; epoch++) {
            double erreurTotale = 0;
            for (int i = 0; i < entrees.length; i++) {
                propagationAvant(entrees[i]);
                erreurTotale += retropropagation(cibles[i], tauxApprentissage);
            }

            if ((epoch + 1) % 1000 == 0 || epoch == 0) {
                System.out.printf("Epoch %d: Erreur moyenne = %.6f\n", epoch + 1, erreurTotale / entrees.length);
            }
        }
    }

    public static void main(String[] args) {
        double[][] entrees = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
        double[][] cibles = {{0}, {1}, {1}, {0}};

        int[] tailleCouches = {2, 3, 1};
        PerceptronMulticouche mlp = new PerceptronMulticouche(tailleCouches);

        System.out.println("Entraînement du MLP sur le problème XOR...");
        mlp.entrainer(entrees, cibles, 10000, 0.1);

        System.out.println("\nTest du réseau:");
        for (int i = 0; i < entrees.length; i++) {
            double[] sortie = mlp.propagationAvant(entrees[i]);
            System.out.printf("Entrées: [%.0f, %.0f], Sortie: %.4f, Attendu: %.0f\n",
                             entrees[i][0], entrees[i][1], sortie[0], cibles[i][0]);
        }
    }
}
