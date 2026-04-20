package tsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class AntColonyOptimization {

    private double c = 1.0;
    private double alpha = 1.0;
    private double beta = 5.0;
    private double evaporation = 0.5;
    private double Q = 500;
    private double antFactor = 0.8;
    private double randomFactor = 0.01;

    private int maxIterations = 1000;

    private int numberOfCities;
    private int numberOfAnts;
    private double[][] graph;
    private double[][] trails;
    private List<Ant> ants = new ArrayList<>();
    private Random random = new Random();
    private double[] probabilities;

    private int[] bestTour;
    private double bestTourLength;

    public AntColonyOptimization(double[][] graph) {
        this.graph = graph;
        this.numberOfCities = graph.length;
        this.numberOfAnts = (int) (numberOfCities * antFactor);

        trails = new double[numberOfCities][numberOfCities];
        probabilities = new double[numberOfCities];
        bestTour = new int[numberOfCities];
        bestTourLength = Double.MAX_VALUE;

        for (int i = 0; i < numberOfAnts; i++) {
            ants.add(new Ant(numberOfCities));
        }
    }

    public int[] solve() {
        setupTrails();

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            setupAnts();
            moveAnts();
            updateTrails();
            updateBest();

            if ((iteration + 1) % 100 == 0) {
                System.out.println("Iteration " + (iteration + 1) + ": Best tour length = " + bestTourLength);
            }
        }

        System.out.println("Best tour length: " + bestTourLength);
        System.out.println("Best tour: " + Arrays.toString(bestTour));

        return bestTour;
    }

    private void setupTrails() {
        for (int i = 0; i < numberOfCities; i++) {
            for (int j = 0; j < numberOfCities; j++) {
                trails[i][j] = c;
            }
        }
    }

    private void setupAnts() {
        for (Ant ant : ants) {
            ant.clear();
            ant.visitCity(-1, random.nextInt(numberOfCities));
        }
    }

    private void moveAnts() {
        for (int i = 0; i < numberOfCities - 1; i++) {
            for (Ant ant : ants) {
                ant.visitCity(i, selectNextCity(ant));
            }
        }
    }

    private int selectNextCity(Ant ant) {
        int currentCity = ant.trail[ant.currentIndex];

        if (random.nextDouble() < randomFactor) {
            int city = random.nextInt(numberOfCities);
            while (ant.visited(city)) city = random.nextInt(numberOfCities);
            return city;
        }

        calculateProbabilities(ant, currentCity);

        double r = random.nextDouble();
        double total = 0;
        for (int i = 0; i < numberOfCities; i++) {
            total += probabilities[i];
            if (total >= r) return i;
        }

        for (int i = 0; i < numberOfCities; i++) {
            if (!ant.visited(i)) return i;
        }

        return -1;
    }

    private void calculateProbabilities(Ant ant, int currentCity) {
        double pheromone = 0.0;

        for (int i = 0; i < numberOfCities; i++) {
            if (!ant.visited(i)) {
                pheromone += Math.pow(trails[currentCity][i], alpha) *
                             Math.pow(1.0 / graph[currentCity][i], beta);
            }
        }

        for (int i = 0; i < numberOfCities; i++) {
            if (ant.visited(i)) {
                probabilities[i] = 0.0;
            } else {
                double numerator = Math.pow(trails[currentCity][i], alpha) *
                                   Math.pow(1.0 / graph[currentCity][i], beta);
                probabilities[i] = numerator / pheromone;
            }
        }
    }

    private void updateTrails() {
        for (int i = 0; i < numberOfCities; i++) {
            for (int j = 0; j < numberOfCities; j++) {
                trails[i][j] *= evaporation;
            }
        }

        for (Ant ant : ants) {
            double contribution = Q / ant.trailLength(graph);
            for (int i = 0; i < numberOfCities - 1; i++) {
                trails[ant.trail[i]][ant.trail[i + 1]] += contribution;
            }
            trails[ant.trail[numberOfCities - 1]][ant.trail[0]] += contribution;
        }
    }

    private void updateBest() {
        for (Ant ant : ants) {
            double length = ant.trailLength(graph);
            if (length < bestTourLength) {
                bestTourLength = length;
                System.arraycopy(ant.trail, 0, bestTour, 0, numberOfCities);
            }
        }
    }

    private class Ant {
        int[] trail;
        boolean[] visited;
        int currentIndex;

        public Ant(int numberOfCities) {
            trail = new int[numberOfCities];
            visited = new boolean[numberOfCities];
            currentIndex = -1;
        }

        public void clear() {
            for (int i = 0; i < numberOfCities; i++) visited[i] = false;
            currentIndex = -1;
        }

        public void visitCity(int index, int city) {
            trail[index + 1] = city;
            visited[city] = true;
            currentIndex = index + 1;
        }

        public boolean visited(int city) {
            return visited[city];
        }

        public double trailLength(double[][] graph) {
            double length = 0.0;
            for (int i = 0; i < numberOfCities - 1; i++) {
                length += graph[trail[i]][trail[i + 1]];
            }
            length += graph[trail[numberOfCities - 1]][trail[0]];
            return length;
        }
    }

    public static double[][] generateRandomMatrix(int numberOfCities) {
        double[][] graph = new double[numberOfCities][numberOfCities];
        Random random = new Random();

        for (int i = 0; i < numberOfCities; i++) {
            for (int j = i + 1; j < numberOfCities; j++) {
                double distance = 100 * random.nextDouble();
                graph[i][j] = distance;
                graph[j][i] = distance;
            }
        }

        return graph;
    }

    public static void main(String[] args) {
        int numberOfCities = 20;
        double[][] graph = generateRandomMatrix(numberOfCities);

        System.out.println("Solving TSP with " + numberOfCities + " cities");

        AntColonyOptimization aco = new AntColonyOptimization(graph);
        int[] bestTour = aco.solve();

        System.out.println("Best tour found: " + Arrays.toString(bestTour));
    }
}
