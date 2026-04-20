package pso;

/**
 * Fonctions de test classiques pour benchmarker PSO.
 * Sphere (convexe simple), Rastrigin (multimodale), Ackley (piégeante).
 * Minimum global de chacune : x = 0, f(x) = 0.
 */
public class FonctionsTest {

    public static final ParticleSwarmOptimization.ObjectiveFunction SPHERE = position -> {
        double sum = 0.0;
        for (double x : position) sum += x * x;
        return sum;
    };

    public static final ParticleSwarmOptimization.ObjectiveFunction RASTRIGIN = position -> {
        double sum = 0.0;
        int n = position.length;
        for (double x : position) {
            sum += x * x - 10 * Math.cos(2 * Math.PI * x);
        }
        return 10 * n + sum;
    };

    public static final ParticleSwarmOptimization.ObjectiveFunction ACKLEY = position -> {
        double sum1 = 0.0;
        double sum2 = 0.0;
        int n = position.length;

        for (double x : position) {
            sum1 += x * x;
            sum2 += Math.cos(2 * Math.PI * x);
        }

        return -20 * Math.exp(-0.2 * Math.sqrt(sum1 / n)) -
               Math.exp(sum2 / n) + 20 + Math.E;
    };

    public static void main(String[] args) {
        int dimension = 5;
        double[] lb = new double[dimension];
        double[] ub = new double[dimension];
        java.util.Arrays.fill(lb, -5.12);
        java.util.Arrays.fill(ub, 5.12);

        System.out.println("=== Benchmark PSO sur trois fonctions ===\n");

        System.out.println(">>> Sphere");
        new ParticleSwarmOptimization(dimension, lb, ub, SPHERE).optimize();

        System.out.println("\n>>> Rastrigin");
        new ParticleSwarmOptimization(dimension, lb, ub, RASTRIGIN).optimize();

        System.out.println("\n>>> Ackley");
        new ParticleSwarmOptimization(dimension, lb, ub, ACKLEY).optimize();
    }
}
