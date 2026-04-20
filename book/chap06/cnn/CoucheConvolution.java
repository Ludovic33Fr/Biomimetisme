package cnn;

import java.util.Random;

public class CoucheConvolution {
    private int profondeurEntree;
    private int hauteurEntree;
    private int largeurEntree;

    private int nombreFiltres;
    private int tailleFiltres;
    private int pas;

    private double[][][][] filtres;
    private double[] biais;

    public CoucheConvolution(int profondeurEntree, int hauteurEntree, int largeurEntree,
                            int nombreFiltres, int tailleFiltres, int pas) {
        this.profondeurEntree = profondeurEntree;
        this.hauteurEntree = hauteurEntree;
        this.largeurEntree = largeurEntree;
        this.nombreFiltres = nombreFiltres;
        this.tailleFiltres = tailleFiltres;
        this.pas = pas;

        filtres = new double[profondeurEntree][tailleFiltres][tailleFiltres][nombreFiltres];
        biais = new double[nombreFiltres];

        Random random = new Random();
        double limite = Math.sqrt(6.0 / (profondeurEntree * tailleFiltres * tailleFiltres + nombreFiltres));

        for (int c = 0; c < profondeurEntree; c++) {
            for (int h = 0; h < tailleFiltres; h++) {
                for (int w = 0; w < tailleFiltres; w++) {
                    for (int f = 0; f < nombreFiltres; f++) {
                        filtres[c][h][w][f] = random.nextDouble() * 2 * limite - limite;
                    }
                }
            }
        }

        for (int f = 0; f < nombreFiltres; f++) {
            biais[f] = random.nextGaussian() * 0.1;
        }
    }

    private double relu(double x) {
        return Math.max(0, x);
    }

    public double[][][] propagationAvant(double[][][] entree) {
        if (entree.length != profondeurEntree ||
            entree[0].length != hauteurEntree ||
            entree[0][0].length != largeurEntree) {
            throw new IllegalArgumentException("Dimensions d'entrée incorrectes");
        }

        int hauteurSortie = (hauteurEntree - tailleFiltres) / pas + 1;
        int largeurSortie = (largeurEntree - tailleFiltres) / pas + 1;

        double[][][] sortie = new double[nombreFiltres][hauteurSortie][largeurSortie];

        for (int f = 0; f < nombreFiltres; f++) {
            for (int hOut = 0; hOut < hauteurSortie; hOut++) {
                for (int wOut = 0; wOut < largeurSortie; wOut++) {
                    int hIn = hOut * pas;
                    int wIn = wOut * pas;

                    double somme = biais[f];

                    for (int c = 0; c < profondeurEntree; c++) {
                        for (int hF = 0; hF < tailleFiltres; hF++) {
                            for (int wF = 0; wF < tailleFiltres; wF++) {
                                somme += entree[c][hIn + hF][wIn + wF] * filtres[c][hF][wF][f];
                            }
                        }
                    }

                    sortie[f][hOut][wOut] = relu(somme);
                }
            }
        }

        return sortie;
    }

    public static void main(String[] args) {
        CoucheConvolution conv = new CoucheConvolution(3, 28, 28, 16, 3, 1);
        double[][][] entree = new double[3][28][28];
        Random random = new Random();
        for (int c = 0; c < 3; c++) {
            for (int h = 0; h < 28; h++) {
                for (int w = 0; w < 28; w++) {
                    entree[c][h][w] = random.nextDouble();
                }
            }
        }

        double[][][] sortie = conv.propagationAvant(entree);
        System.out.println("Sortie : " + sortie.length + " filtres de " + sortie[0].length + "x" + sortie[0][0].length);
    }
}
