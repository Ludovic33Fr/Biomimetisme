package hebbien;

import java.util.Random;

public class ReseauHebbien {
    private int tailleEntree;
    private int tailleSortie;
    private double[][] poids;
    private double tauxApprentissage;

    public ReseauHebbien(int tailleEntree, int tailleSortie, double tauxApprentissage) {
        this.tailleEntree = tailleEntree;
        this.tailleSortie = tailleSortie;
        this.tauxApprentissage = tauxApprentissage;

        poids = new double[tailleEntree][tailleSortie];
        Random random = new Random();
        for (int i = 0; i < tailleEntree; i++) {
            for (int j = 0; j < tailleSortie; j++) {
                poids[i][j] = random.nextGaussian() * 0.1;
            }
        }
    }

    public double[] propagationAvant(double[] entree) {
        if (entree.length != tailleEntree) {
            throw new IllegalArgumentException("Taille d'entrée incorrecte");
        }

        double[] sortie = new double[tailleSortie];

        for (int j = 0; j < tailleSortie; j++) {
            for (int i = 0; i < tailleEntree; i++) {
                sortie[j] += entree[i] * poids[i][j];
            }
            sortie[j] = 1.0 / (1.0 + Math.exp(-sortie[j]));
        }

        return sortie;
    }

    /**
     * Règle de Hebb : Δw = η * x * y, avec normalisation pour éviter la croissance illimitée.
     */
    public void apprendreHebb(double[] entree, double[] sortie) {
        for (int i = 0; i < tailleEntree; i++) {
            for (int j = 0; j < tailleSortie; j++) {
                poids[i][j] += tauxApprentissage * entree[i] * sortie[j];
            }
        }
        normaliserPoids();
    }

    /**
     * Règle d'Oja : Δw = η * y * (x - y * w).
     * Intègre la normalisation dans la mise à jour, réalise implicitement une ACP.
     */
    public void apprendreOja(double[] entree, double[] sortie) {
        for (int i = 0; i < tailleEntree; i++) {
            for (int j = 0; j < tailleSortie; j++) {
                poids[i][j] += tauxApprentissage * sortie[j] * (entree[i] - sortie[j] * poids[i][j]);
            }
        }
    }

    private void normaliserPoids() {
        for (int j = 0; j < tailleSortie; j++) {
            double sommeCarres = 0;
            for (int i = 0; i < tailleEntree; i++) sommeCarres += poids[i][j] * poids[i][j];
            double norme = Math.sqrt(sommeCarres);
            if (norme > 0) {
                for (int i = 0; i < tailleEntree; i++) poids[i][j] /= norme;
            }
        }
    }

    public void afficherPoids() {
        System.out.println("Poids du réseau:");
        for (int i = 0; i < tailleEntree; i++) {
            for (int j = 0; j < tailleSortie; j++) {
                System.out.printf("%.4f ", poids[i][j]);
            }
            System.out.println();
        }
    }

    public static void main(String[] args) {
        ReseauHebbien reseau = new ReseauHebbien(3, 2, 0.1);

        System.out.println("Poids initiaux:");
        reseau.afficherPoids();

        double[][] exemples = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 0}};

        System.out.println("\nApprentissage...");
        for (int epoch = 0; epoch < 100; epoch++) {
            for (double[] exemple : exemples) {
                double[] sortie = reseau.propagationAvant(exemple);
                reseau.apprendreHebb(exemple, sortie);
            }
        }

        System.out.println("\nPoids après apprentissage:");
        reseau.afficherPoids();

        System.out.println("\nTest du réseau:");
        for (double[] exemple : exemples) {
            double[] sortie = reseau.propagationAvant(exemple);
            System.out.print("Entrée: [");
            for (double v : exemple) System.out.printf("%.0f ", v);
            System.out.print("], Sortie: [");
            for (double v : sortie) System.out.printf("%.4f ", v);
            System.out.println("]");
        }
    }
}
