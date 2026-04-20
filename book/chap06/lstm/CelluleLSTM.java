package lstm;

import java.util.Arrays;
import java.util.Random;

public class CelluleLSTM {
    private int tailleEntree;
    private int tailleEtatCache;

    private double[][] Wf;
    private double[] bf;
    private double[][] Wi;
    private double[] bi;
    private double[][] Wc;
    private double[] bc;
    private double[][] Wo;
    private double[] bo;

    private double[] etatCellule;
    private double[] etatCache;

    public CelluleLSTM(int tailleEntree, int tailleEtatCache) {
        this.tailleEntree = tailleEntree;
        this.tailleEtatCache = tailleEtatCache;

        Random random = new Random();

        Wf = new double[tailleEntree + tailleEtatCache][tailleEtatCache];
        bf = new double[tailleEtatCache];
        initialiserPoids(Wf, bf, random);

        Wi = new double[tailleEntree + tailleEtatCache][tailleEtatCache];
        bi = new double[tailleEtatCache];
        initialiserPoids(Wi, bi, random);

        Wc = new double[tailleEntree + tailleEtatCache][tailleEtatCache];
        bc = new double[tailleEtatCache];
        initialiserPoids(Wc, bc, random);

        Wo = new double[tailleEntree + tailleEtatCache][tailleEtatCache];
        bo = new double[tailleEtatCache];
        initialiserPoids(Wo, bo, random);

        etatCellule = new double[tailleEtatCache];
        etatCache = new double[tailleEtatCache];
    }

    private void initialiserPoids(double[][] poids, double[] biais, Random random) {
        for (int i = 0; i < poids.length; i++) {
            for (int j = 0; j < poids[i].length; j++) {
                poids[i][j] = random.nextGaussian() * 0.1;
            }
        }

        for (int i = 0; i < biais.length; i++) {
            biais[i] = random.nextGaussian() * 0.1;
        }
    }

    private double sigmoide(double x) { return 1.0 / (1.0 + Math.exp(-x)); }
    private double tanh(double x) { return Math.tanh(x); }

    public double[] propagationAvant(double[] entree) {
        double[] x = new double[tailleEntree + tailleEtatCache];
        System.arraycopy(entree, 0, x, 0, tailleEntree);
        System.arraycopy(etatCache, 0, x, tailleEntree, tailleEtatCache);

        double[] f = new double[tailleEtatCache];
        for (int i = 0; i < tailleEtatCache; i++) {
            double somme = bf[i];
            for (int j = 0; j < x.length; j++) somme += x[j] * Wf[j][i];
            f[i] = sigmoide(somme);
        }

        double[] inputGate = new double[tailleEtatCache];
        for (int j = 0; j < tailleEtatCache; j++) {
            double somme = bi[j];
            for (int k = 0; k < x.length; k++) somme += x[k] * Wi[k][j];
            inputGate[j] = sigmoide(somme);
        }

        double[] c_tilde = new double[tailleEtatCache];
        for (int j = 0; j < tailleEtatCache; j++) {
            double somme = bc[j];
            for (int k = 0; k < x.length; k++) somme += x[k] * Wc[k][j];
            c_tilde[j] = tanh(somme);
        }

        for (int j = 0; j < tailleEtatCache; j++) {
            etatCellule[j] = f[j] * etatCellule[j] + inputGate[j] * c_tilde[j];
        }

        double[] o = new double[tailleEtatCache];
        for (int j = 0; j < tailleEtatCache; j++) {
            double somme = bo[j];
            for (int k = 0; k < x.length; k++) somme += x[k] * Wo[k][j];
            o[j] = sigmoide(somme);
        }

        for (int j = 0; j < tailleEtatCache; j++) {
            etatCache[j] = o[j] * tanh(etatCellule[j]);
        }

        return etatCache;
    }

    public void reinitialiserEtats() {
        Arrays.fill(etatCellule, 0);
        Arrays.fill(etatCache, 0);
    }

    public static void main(String[] args) {
        CelluleLSTM cellule = new CelluleLSTM(3, 5);
        double[] entree = {1.0, 0.5, -0.2};
        double[] sortie = cellule.propagationAvant(entree);
        System.out.println("État caché après propagation : " + Arrays.toString(sortie));
    }
}
