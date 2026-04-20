package logistique;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * ACO appliqué au Capacitated Vehicle Routing Problem (CVRP).
 * Variante avancée avec paramètre q0 (exploitation/exploration) façon ACS.
 */
public class AntColonyVRP {

    private final int numClients;
    private final int numVehicles;
    private final int vehicleCapacity;
    private final int depot = 0;
    private final double[][] distances;
    private final int[] demands;

    private final int numAnts;
    private final int maxIterations;
    private final double alpha;
    private final double beta;
    private final double rho;
    private final double q0;

    private double[][] pheromones;
    private double[][] heuristic;
    private List<Integer>[] bestSolution;
    private double bestSolutionCost;

    public AntColonyVRP(double[][] distances, int[] demands, int numVehicles, int vehicleCapacity,
                        int numAnts, int maxIterations, double alpha, double beta, double rho, double q0) {
        this.numClients = distances.length - 1;
        this.numVehicles = numVehicles;
        this.vehicleCapacity = vehicleCapacity;
        this.distances = distances;
        this.demands = demands;

        this.numAnts = numAnts;
        this.maxIterations = maxIterations;
        this.alpha = alpha;
        this.beta = beta;
        this.rho = rho;
        this.q0 = q0;

        initializePheromones();
        initializeHeuristic();

        bestSolutionCost = Double.POSITIVE_INFINITY;
    }

    private void initializePheromones() {
        int n = numClients + 1;
        pheromones = new double[n][n];

        double initialValue = 1.0 / (n * approximateSolutionCost());

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) pheromones[i][j] = initialValue;
            }
        }
    }

    private double approximateSolutionCost() {
        double totalDistance = 0;
        int count = 0;

        for (int i = 0; i < distances.length; i++) {
            for (int j = 0; j < distances[i].length; j++) {
                if (i != j) {
                    totalDistance += distances[i][j];
                    count++;
                }
            }
        }

        return totalDistance / count * numClients;
    }

    private void initializeHeuristic() {
        int n = numClients + 1;
        heuristic = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) heuristic[i][j] = 1.0 / distances[i][j];
            }
        }
    }

    @SuppressWarnings("unchecked")
    public List<Integer>[] solve() {
        bestSolution = new ArrayList[numVehicles];

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            List<Integer>[][] antSolutions = constructAntSolutions();
            double[] antSolutionCosts = calculateSolutionCosts(antSolutions);

            for (int ant = 0; ant < numAnts; ant++) {
                if (antSolutionCosts[ant] < bestSolutionCost) {
                    bestSolutionCost = antSolutionCosts[ant];
                    bestSolution = antSolutions[ant].clone();
                }
            }

            updatePheromones(antSolutions, antSolutionCosts);

            if ((iteration + 1) % 10 == 0) {
                System.out.println("Itération " + (iteration + 1) + ", coût: " + bestSolutionCost);
            }
        }

        return bestSolution;
    }

    @SuppressWarnings("unchecked")
    private List<Integer>[][] constructAntSolutions() {
        List<Integer>[][] antSolutions = new ArrayList[numAnts][numVehicles];

        for (int ant = 0; ant < numAnts; ant++) {
            for (int v = 0; v < numVehicles; v++) {
                antSolutions[ant][v] = new ArrayList<>();
            }

            List<Integer> unvisitedClients = new ArrayList<>();
            for (int i = 1; i <= numClients; i++) unvisitedClients.add(i);

            for (int v = 0; v < numVehicles && !unvisitedClients.isEmpty(); v++) {
                int currentCapacity = vehicleCapacity;
                int currentPosition = depot;

                antSolutions[ant][v].add(depot);

                while (!unvisitedClients.isEmpty()) {
                    int nextClient = selectNextClient(currentPosition, unvisitedClients, currentCapacity);
                    if (nextClient == -1) break;

                    antSolutions[ant][v].add(nextClient);
                    currentCapacity -= demands[nextClient];
                    currentPosition = nextClient;
                    unvisitedClients.remove(Integer.valueOf(nextClient));
                }

                antSolutions[ant][v].add(depot);
            }
        }

        return antSolutions;
    }

    private int selectNextClient(int currentPosition, List<Integer> unvisitedClients, int remainingCapacity) {
        if (unvisitedClients.isEmpty()) return -1;

        List<Integer> feasibleClients = new ArrayList<>();
        for (int client : unvisitedClients) {
            if (demands[client] <= remainingCapacity) feasibleClients.add(client);
        }

        if (feasibleClients.isEmpty()) return -1;

        Random random = new Random();
        if (random.nextDouble() < q0) {
            double maxValue = -1;
            int nextClient = -1;

            for (int client : feasibleClients) {
                double value = Math.pow(pheromones[currentPosition][client], alpha) *
                               Math.pow(heuristic[currentPosition][client], beta);
                if (value > maxValue) {
                    maxValue = value;
                    nextClient = client;
                }
            }

            return nextClient;
        } else {
            double[] probabilities = new double[feasibleClients.size()];
            double sum = 0;

            for (int i = 0; i < feasibleClients.size(); i++) {
                int client = feasibleClients.get(i);
                probabilities[i] = Math.pow(pheromones[currentPosition][client], alpha) *
                                   Math.pow(heuristic[currentPosition][client], beta);
                sum += probabilities[i];
            }

            for (int i = 0; i < probabilities.length; i++) probabilities[i] /= sum;

            double r = random.nextDouble();
            double cumul = 0;

            for (int i = 0; i < probabilities.length; i++) {
                cumul += probabilities[i];
                if (r <= cumul) return feasibleClients.get(i);
            }

            return feasibleClients.get(feasibleClients.size() - 1);
        }
    }

    private double[] calculateSolutionCosts(List<Integer>[][] antSolutions) {
        double[] costs = new double[numAnts];

        for (int ant = 0; ant < numAnts; ant++) {
            double totalDistance = 0;

            for (int v = 0; v < numVehicles; v++) {
                List<Integer> route = antSolutions[ant][v];
                if (route.size() <= 2) continue;

                for (int i = 0; i < route.size() - 1; i++) {
                    totalDistance += distances[route.get(i)][route.get(i + 1)];
                }
            }

            costs[ant] = totalDistance;
        }

        return costs;
    }

    private void updatePheromones(List<Integer>[][] antSolutions, double[] antSolutionCosts) {
        for (int i = 0; i < pheromones.length; i++) {
            for (int j = 0; j < pheromones[i].length; j++) {
                if (i != j) pheromones[i][j] *= (1 - rho);
            }
        }

        for (int ant = 0; ant < numAnts; ant++) {
            double contribution = 1.0 / antSolutionCosts[ant];

            for (int v = 0; v < numVehicles; v++) {
                List<Integer> route = antSolutions[ant][v];
                for (int i = 0; i < route.size() - 1; i++) {
                    int from = route.get(i);
                    int to = route.get(i + 1);
                    pheromones[from][to] += contribution;
                    pheromones[to][from] += contribution;
                }
            }
        }
    }

    public double getBestSolutionCost() {
        return bestSolutionCost;
    }

    public static void main(String[] args) {
        double[][] coordinates = {
            {50, 50}, {45, 68}, {45, 70}, {42, 66}, {42, 68}, {42, 65},
            {40, 69}, {40, 66}, {38, 68}, {38, 70}, {35, 66}, {35, 69},
            {25, 85}, {22, 75}, {22, 85}, {20, 80}, {20, 85}, {18, 75},
            {15, 75}, {15, 80}, {30, 50}
        };

        int n = coordinates.length;
        double[][] distances = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                distances[i][j] = Math.sqrt(
                    Math.pow(coordinates[i][0] - coordinates[j][0], 2) +
                    Math.pow(coordinates[i][1] - coordinates[j][1], 2)
                );
            }
        }

        int[] demands = {0, 10, 7, 13, 19, 26, 3, 5, 9, 16, 16, 12, 19, 23, 20, 8, 19, 2, 12, 17, 9};

        AntColonyVRP aco = new AntColonyVRP(distances, demands, 5, 100, 20, 100, 1.0, 2.0, 0.1, 0.9);

        List<Integer>[] solution = aco.solve();

        System.out.println("Meilleure solution (coût: " + aco.getBestSolutionCost() + "):");
        for (int v = 0; v < solution.length; v++) {
            if (solution[v].size() > 2) {
                System.out.print("Véhicule " + (v + 1) + ": ");
                for (int i = 0; i < solution[v].size(); i++) {
                    System.out.print(solution[v].get(i));
                    if (i < solution[v].size() - 1) System.out.print(" -> ");
                }
                System.out.println();
            }
        }
    }
}
