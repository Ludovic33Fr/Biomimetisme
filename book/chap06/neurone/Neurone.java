package neurone;

import java.util.Random;

public class Neurone {
    private double[] poids;
    private double biais;
    private FonctionActivation fonctionActivation;

    public interface FonctionActivation {
        double calculer(double x);
        double derivee(double x);
    }

    public static class Sigmoide implements FonctionActivation {
        @Override public double calculer(double x) { return 1.0 / (1.0 + Math.exp(-x)); }
        @Override public double derivee(double x) { double fx = calculer(x); return fx * (1 - fx); }
    }

    public static class TanH implements FonctionActivation {
        @Override public double calculer(double x) { return Math.tanh(x); }
        @Override public double derivee(double x) { double fx = calculer(x); return 1 - fx * fx; }
    }

    public static class ReLU implements FonctionActivation {
        @Override public double calculer(double x) { return Math.max(0, x); }
        @Override public double derivee(double x) { return x > 0 ? 1 : 0; }
    }

    public Neurone(int nbEntrees, FonctionActivation fonctionActivation) {
        this.poids = new double[nbEntrees];
        this.fonctionActivation = fonctionActivation;

        Random random = new Random();
        for (int i = 0; i < nbEntrees; i++) {
            this.poids[i] = random.nextGaussian() * 0.1;
        }
        this.biais = random.nextGaussian() * 0.1;
    }

    public double calculerSortie(double[] entrees) {
        if (entrees.length != poids.length) {
            throw new IllegalArgumentException("Le nombre d'entrées ne correspond pas au nombre de poids");
        }

        double somme = biais;
        for (int i = 0; i < entrees.length; i++) {
            somme += poids[i] * entrees[i];
        }

        return fonctionActivation.calculer(somme);
    }

    public double apprendre(double[] entrees, double sortieAttendue, double tauxApprentissage) {
        double sortieActuelle = calculerSortie(entrees);
        double erreur = sortieAttendue - sortieActuelle;

        for (int i = 0; i < poids.length; i++) {
            poids[i] += tauxApprentissage * erreur * entrees[i];
        }
        biais += tauxApprentissage * erreur;

        return erreur;
    }

    public void afficherPoids() {
        System.out.println("Poids du neurone:");
        for (int i = 0; i < poids.length; i++) {
            System.out.printf("w%d = %.4f\n", i, poids[i]);
        }
        System.out.printf("Biais = %.4f\n", biais);
    }

    public static void main(String[] args) {
        Neurone neurone = new Neurone(2, new Sigmoide());

        double[][] entrees = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};
        double[] sortiesAttendues = {0, 0, 0, 1};

        System.out.println("Avant apprentissage:");
        neurone.afficherPoids();

        int epochs = 10000;
        double tauxApprentissage = 0.1;

        for (int epoch = 0; epoch < epochs; epoch++) {
            double erreurTotale = 0;
            for (int i = 0; i < entrees.length; i++) {
                double erreur = neurone.apprendre(entrees[i], sortiesAttendues[i], tauxApprentissage);
                erreurTotale += Math.abs(erreur);
            }

            if ((epoch + 1) % 1000 == 0) {
                System.out.printf("Epoch %d: Erreur totale = %.4f\n", epoch + 1, erreurTotale);
            }

            if (erreurTotale < 0.01) {
                System.out.printf("Convergence atteinte à l'epoch %d\n", epoch + 1);
                break;
            }
        }

        System.out.println("\nAprès apprentissage:");
        neurone.afficherPoids();

        System.out.println("\nTest du neurone:");
        for (int i = 0; i < entrees.length; i++) {
            double sortie = neurone.calculerSortie(entrees[i]);
            System.out.printf("Entrées: [%.0f, %.0f], Sortie: %.4f, Attendu: %.0f\n",
                             entrees[i][0], entrees[i][1], sortie, sortiesAttendues[i]);
        }
    }
}
