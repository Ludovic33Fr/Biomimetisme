package rosenbrock;

import java.util.Random;

public class StrategieEvolution {

    private static final int DIMENSIONS = 2;
    private static final double SIGMA_INITIAL = 1.0;
    private static final double FACTEUR_ADAPTATION = 0.817;
    private static final int MAX_ITERATIONS = 1000;

    private Random random = new Random();
    private double[] solution;
    private double sigma;

    public StrategieEvolution() {
        solution = new double[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            solution[i] = random.nextDouble() * 4.0 - 2.0;
        }
        sigma = SIGMA_INITIAL;
    }

    private double evaluer(double[] x) {
        double somme = 0.0;
        for (int i = 0; i < DIMENSIONS - 1; i++) {
            somme += 100 * Math.pow(x[i+1] - x[i]*x[i], 2) + Math.pow(1 - x[i], 2);
        }
        return somme;
    }

    private double[] muter(double[] parent) {
        double[] enfant = new double[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            enfant[i] = parent[i] + sigma * random.nextGaussian();
        }
        return enfant;
    }

    public void executer() {
        double fitnessParent = evaluer(solution);
        int succes = 0;

        System.out.println("Début de la stratégie d'évolution (1+1)-ES");
        System.out.println("Fitness initiale: " + fitnessParent);

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            double[] enfant = muter(solution);
            double fitnessEnfant = evaluer(enfant);

            if (fitnessEnfant <= fitnessParent) {
                solution = enfant;
                fitnessParent = fitnessEnfant;
                succes++;
            }

            if (iteration % 10 == 0) {
                double tauxSucces = succes / 10.0;
                if (tauxSucces > 0.2) {
                    sigma /= FACTEUR_ADAPTATION;
                } else if (tauxSucces < 0.2) {
                    sigma *= FACTEUR_ADAPTATION;
                }
                succes = 0;
            }

            if (iteration % 100 == 0) {
                System.out.println("Itération " + iteration + ": Fitness = " + fitnessParent + ", Sigma = " + sigma);
            }
        }

        System.out.println("\nRésultat final:");
        System.out.println("Fitness = " + fitnessParent);
        System.out.print("Solution = [");
        for (int i = 0; i < DIMENSIONS; i++) {
            System.out.print(solution[i]);
            if (i < DIMENSIONS - 1) System.out.print(", ");
        }
        System.out.println("]");
        System.out.println("Optimum théorique = [1.0, 1.0] avec fitness = 0.0");
    }

    public static void main(String[] args) {
        StrategieEvolution se = new StrategieEvolution();
        se.executer();
    }
}
