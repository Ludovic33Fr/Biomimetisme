package pso;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EssaimsParticulaires {

    private static final int NB_PARTICULES = 30;
    private static final int NB_DIMENSIONS = 2;
    private static final int NB_ITERATIONS = 100;
    private static final double W = 0.729;
    private static final double C1 = 1.49445;
    private static final double C2 = 1.49445;

    private Random random = new Random();

    private class Particule {
        private double[] position;
        private double[] vitesse;
        private double[] meilleurePosition;
        private double meilleureFitness;
        private double fitness;

        public Particule() {
            position = new double[NB_DIMENSIONS];
            vitesse = new double[NB_DIMENSIONS];
            meilleurePosition = new double[NB_DIMENSIONS];

            for (int i = 0; i < NB_DIMENSIONS; i++) {
                position[i] = random.nextDouble() * 10 - 5;
                vitesse[i] = random.nextDouble() * 2 - 1;
                meilleurePosition[i] = position[i];
            }

            fitness = calculerFitness(position);
            meilleureFitness = fitness;
        }

        public void mettreAJour(double[] meilleurePositionGlobale) {
            for (int i = 0; i < NB_DIMENSIONS; i++) {
                double r1 = random.nextDouble();
                double r2 = random.nextDouble();

                vitesse[i] = W * vitesse[i] +
                            C1 * r1 * (meilleurePosition[i] - position[i]) +
                            C2 * r2 * (meilleurePositionGlobale[i] - position[i]);

                if (vitesse[i] > 1) vitesse[i] = 1;
                if (vitesse[i] < -1) vitesse[i] = -1;
            }

            for (int i = 0; i < NB_DIMENSIONS; i++) {
                position[i] += vitesse[i];
                if (position[i] > 5) position[i] = 5;
                if (position[i] < -5) position[i] = -5;
            }

            fitness = calculerFitness(position);

            if (fitness > meilleureFitness) {
                meilleureFitness = fitness;
                System.arraycopy(position, 0, meilleurePosition, 0, NB_DIMENSIONS);
            }
        }
    }

    private double calculerFitness(double[] position) {
        double somme = 10 * NB_DIMENSIONS;
        for (int i = 0; i < NB_DIMENSIONS; i++) {
            somme += position[i] * position[i] - 10 * Math.cos(2 * Math.PI * position[i]);
        }
        return 1.0 / (somme + 1e-10);
    }

    public void executer() {
        List<Particule> essaim = new ArrayList<>();
        for (int i = 0; i < NB_PARTICULES; i++) {
            essaim.add(new Particule());
        }

        double[] meilleurePositionGlobale = new double[NB_DIMENSIONS];
        double meilleureFitnessGlobale = Double.NEGATIVE_INFINITY;

        for (Particule p : essaim) {
            if (p.meilleureFitness > meilleureFitnessGlobale) {
                meilleureFitnessGlobale = p.meilleureFitness;
                System.arraycopy(p.meilleurePosition, 0, meilleurePositionGlobale, 0, NB_DIMENSIONS);
            }
        }

        System.out.println("Itération 0: " + formatPosition(meilleurePositionGlobale) +
                          " = " + (1.0 / meilleureFitnessGlobale - 1e-10));

        for (int iteration = 1; iteration <= NB_ITERATIONS; iteration++) {
            for (Particule p : essaim) {
                p.mettreAJour(meilleurePositionGlobale);

                if (p.meilleureFitness > meilleureFitnessGlobale) {
                    meilleureFitnessGlobale = p.meilleureFitness;
                    System.arraycopy(p.meilleurePosition, 0, meilleurePositionGlobale, 0, NB_DIMENSIONS);
                }
            }

            if (iteration % 10 == 0 || iteration == NB_ITERATIONS) {
                System.out.println("Itération " + iteration + ": " + formatPosition(meilleurePositionGlobale) +
                                  " = " + (1.0 / meilleureFitnessGlobale - 1e-10));
            }
        }
    }

    private String formatPosition(double[] position) {
        StringBuilder sb = new StringBuilder("f(");
        for (int i = 0; i < NB_DIMENSIONS; i++) {
            sb.append(String.format("%.6f", position[i]));
            if (i < NB_DIMENSIONS - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    public static void main(String[] args) {
        EssaimsParticulaires pso = new EssaimsParticulaires();
        pso.executer();
    }
}
