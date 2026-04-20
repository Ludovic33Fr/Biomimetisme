package pso;

import java.util.Arrays;
import java.util.Random;

public class ParticleSwarmOptimization {

    private int swarmSize;
    private int dimension;
    private int maxIterations;
    private double[] lowerBounds;
    private double[] upperBounds;

    private double w;
    private double c1;
    private double c2;

    private Particle[] particles;
    private double[] globalBestPosition;
    private double globalBestFitness;

    private ObjectiveFunction objectiveFunction;

    public interface ObjectiveFunction {
        double evaluate(double[] position);
    }

    public ParticleSwarmOptimization(int dimension, double[] lowerBounds, double[] upperBounds,
                                    ObjectiveFunction objectiveFunction) {
        this(30, dimension, 1000, lowerBounds, upperBounds, 0.729, 1.49445, 1.49445, objectiveFunction);
    }

    public ParticleSwarmOptimization(int swarmSize, int dimension, int maxIterations,
                                    double[] lowerBounds, double[] upperBounds,
                                    double w, double c1, double c2,
                                    ObjectiveFunction objectiveFunction) {
        this.swarmSize = swarmSize;
        this.dimension = dimension;
        this.maxIterations = maxIterations;
        this.lowerBounds = lowerBounds;
        this.upperBounds = upperBounds;
        this.w = w;
        this.c1 = c1;
        this.c2 = c2;
        this.objectiveFunction = objectiveFunction;

        particles = new Particle[swarmSize];
        globalBestPosition = new double[dimension];
        globalBestFitness = Double.MAX_VALUE;
    }

    public double[] optimize() {
        Random random = new Random();

        for (int i = 0; i < swarmSize; i++) {
            particles[i] = new Particle(dimension);

            for (int d = 0; d < dimension; d++) {
                particles[i].position[d] = lowerBounds[d] +
                                         (upperBounds[d] - lowerBounds[d]) * random.nextDouble();
                particles[i].velocity[d] = (upperBounds[d] - lowerBounds[d]) *
                                         (random.nextDouble() - 0.5) * 0.1;
            }

            particles[i].fitness = objectiveFunction.evaluate(particles[i].position);

            System.arraycopy(particles[i].position, 0, particles[i].personalBestPosition, 0, dimension);
            particles[i].personalBestFitness = particles[i].fitness;

            if (particles[i].fitness < globalBestFitness) {
                globalBestFitness = particles[i].fitness;
                System.arraycopy(particles[i].position, 0, globalBestPosition, 0, dimension);
            }
        }

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            for (int i = 0; i < swarmSize; i++) {
                Particle particle = particles[i];

                for (int d = 0; d < dimension; d++) {
                    double r1 = random.nextDouble();
                    double r2 = random.nextDouble();

                    particle.velocity[d] = w * particle.velocity[d] +
                                         c1 * r1 * (particle.personalBestPosition[d] - particle.position[d]) +
                                         c2 * r2 * (globalBestPosition[d] - particle.position[d]);

                    double maxVelocity = 0.1 * (upperBounds[d] - lowerBounds[d]);
                    if (particle.velocity[d] > maxVelocity) particle.velocity[d] = maxVelocity;
                    else if (particle.velocity[d] < -maxVelocity) particle.velocity[d] = -maxVelocity;
                }

                for (int d = 0; d < dimension; d++) {
                    particle.position[d] += particle.velocity[d];

                    if (particle.position[d] < lowerBounds[d]) {
                        particle.position[d] = lowerBounds[d];
                        particle.velocity[d] *= -0.5;
                    } else if (particle.position[d] > upperBounds[d]) {
                        particle.position[d] = upperBounds[d];
                        particle.velocity[d] *= -0.5;
                    }
                }

                particle.fitness = objectiveFunction.evaluate(particle.position);

                if (particle.fitness < particle.personalBestFitness) {
                    particle.personalBestFitness = particle.fitness;
                    System.arraycopy(particle.position, 0, particle.personalBestPosition, 0, dimension);

                    if (particle.fitness < globalBestFitness) {
                        globalBestFitness = particle.fitness;
                        System.arraycopy(particle.position, 0, globalBestPosition, 0, dimension);
                    }
                }
            }

            if ((iteration + 1) % 100 == 0 || iteration == 0) {
                System.out.println("Iteration " + (iteration + 1) + ": Best fitness = " + globalBestFitness);
            }
        }

        System.out.println("Optimization completed!");
        System.out.println("Best fitness: " + globalBestFitness);
        System.out.println("Best position: " + Arrays.toString(globalBestPosition));

        return globalBestPosition;
    }

    private static class Particle {
        double[] position;
        double[] velocity;
        double[] personalBestPosition;
        double fitness;
        double personalBestFitness;

        public Particle(int dimension) {
            position = new double[dimension];
            velocity = new double[dimension];
            personalBestPosition = new double[dimension];
            fitness = Double.MAX_VALUE;
            personalBestFitness = Double.MAX_VALUE;
        }
    }

    public static void main(String[] args) {
        int dimension = 2;
        double[] lowerBounds = {-5.0, -5.0};
        double[] upperBounds = {5.0, 5.0};

        ObjectiveFunction rosenbrockFunction = position -> {
            double x = position[0];
            double y = position[1];
            return Math.pow(1 - x, 2) + 100 * Math.pow(y - x * x, 2);
        };

        System.out.println("Optimizing Rosenbrock function...");

        ParticleSwarmOptimization pso = new ParticleSwarmOptimization(
            dimension, lowerBounds, upperBounds, rosenbrockFunction);

        double[] bestPosition = pso.optimize();

        System.out.println("Expected optimum: [1.0, 1.0]");
        System.out.println("Found optimum: " + Arrays.toString(bestPosition));
    }
}
