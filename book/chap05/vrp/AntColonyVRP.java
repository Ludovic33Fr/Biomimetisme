package vrp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AntColonyVRP {

    private double alpha = 1.0;
    private double beta = 2.0;
    private double evaporation = 0.5;
    private double Q = 100;
    private int maxIterations = 500;
    private int antCount = 20;

    private int vehicleCount;
    private int customerCount;
    private double[][] distances;
    private double[] demands;
    private double vehicleCapacity;
    private int depot = 0;

    private double[][] pheromones;
    private List<Route> bestSolution;
    private double bestSolutionCost;

    public AntColonyVRP(double[][] distances, double[] demands, int vehicleCount, double vehicleCapacity) {
        this.distances = distances;
        this.demands = demands;
        this.vehicleCount = vehicleCount;
        this.vehicleCapacity = vehicleCapacity;
        this.customerCount = distances.length - 1;

        pheromones = new double[distances.length][distances.length];
        for (int i = 0; i < distances.length; i++) {
            for (int j = 0; j < distances.length; j++) {
                pheromones[i][j] = 1.0;
            }
        }

        bestSolutionCost = Double.MAX_VALUE;
    }

    public List<Route> solve() {
        Random random = new Random();

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            List<List<Route>> antSolutions = new ArrayList<>();

            for (int ant = 0; ant < antCount; ant++) {
                List<Route> solution = constructSolution(random);
                antSolutions.add(solution);

                double solutionCost = calculateSolutionCost(solution);
                if (solutionCost < bestSolutionCost) {
                    bestSolutionCost = solutionCost;
                    bestSolution = new ArrayList<>(solution);
                }
            }

            updatePheromones(antSolutions);

            if ((iteration + 1) % 50 == 0) {
                System.out.println("Iteration " + (iteration + 1) + ": Best solution cost = " + bestSolutionCost);
            }
        }

        System.out.println("Best solution cost: " + bestSolutionCost);
        return bestSolution;
    }

    private List<Route> constructSolution(Random random) {
        List<Route> solution = new ArrayList<>();
        boolean[] visited = new boolean[customerCount + 1];
        visited[depot] = true;

        for (int v = 0; v < vehicleCount; v++) {
            solution.add(new Route(vehicleCapacity));
        }

        int remainingCustomers = customerCount;
        int safetyCounter = customerCount * 10;
        while (remainingCustomers > 0 && safetyCounter-- > 0) {
            int vehicleIndex = random.nextInt(vehicleCount);
            Route route = solution.get(vehicleIndex);

            if (route.nodes.isEmpty()) route.nodes.add(depot);

            int currentNode = route.nodes.get(route.nodes.size() - 1);
            int nextNode = selectNextNode(currentNode, visited, route.remainingCapacity, random);

            if (nextNode != -1) {
                route.nodes.add(nextNode);
                route.remainingCapacity -= demands[nextNode];
                visited[nextNode] = true;
                remainingCustomers--;
            } else {
                if (!route.nodes.isEmpty() && route.nodes.get(route.nodes.size() - 1) != depot) {
                    route.nodes.add(depot);
                }
            }
        }

        for (Route route : solution) {
            if (!route.nodes.isEmpty() && route.nodes.get(route.nodes.size() - 1) != depot) {
                route.nodes.add(depot);
            }
        }

        return solution;
    }

    private int selectNextNode(int currentNode, boolean[] visited, double remainingCapacity, Random random) {
        List<Integer> candidates = new ArrayList<>();
        List<Double> probabilities = new ArrayList<>();
        double totalProbability = 0.0;

        for (int i = 1; i <= customerCount; i++) {
            if (!visited[i] && demands[i] <= remainingCapacity) {
                candidates.add(i);
                double pheromone = pheromones[currentNode][i];
                double heuristic = 1.0 / distances[currentNode][i];
                double probability = Math.pow(pheromone, alpha) * Math.pow(heuristic, beta);
                probabilities.add(probability);
                totalProbability += probability;
            }
        }

        if (candidates.isEmpty()) return -1;

        double r = random.nextDouble() * totalProbability;
        double cumulativeProbability = 0.0;

        for (int i = 0; i < candidates.size(); i++) {
            cumulativeProbability += probabilities.get(i);
            if (cumulativeProbability >= r) return candidates.get(i);
        }

        return candidates.get(candidates.size() - 1);
    }

    private void updatePheromones(List<List<Route>> antSolutions) {
        for (int i = 0; i < pheromones.length; i++) {
            for (int j = 0; j < pheromones.length; j++) {
                pheromones[i][j] *= (1.0 - evaporation);
            }
        }

        for (List<Route> solution : antSolutions) {
            double solutionCost = calculateSolutionCost(solution);
            if (solutionCost == 0) continue;
            double contribution = Q / solutionCost;

            for (Route route : solution) {
                for (int i = 0; i < route.nodes.size() - 1; i++) {
                    int from = route.nodes.get(i);
                    int to = route.nodes.get(i + 1);
                    pheromones[from][to] += contribution;
                    pheromones[to][from] += contribution;
                }
            }
        }
    }

    private double calculateSolutionCost(List<Route> solution) {
        double totalCost = 0.0;
        for (Route route : solution) {
            if (route.nodes.size() <= 1) continue;
            for (int i = 0; i < route.nodes.size() - 1; i++) {
                int from = route.nodes.get(i);
                int to = route.nodes.get(i + 1);
                totalCost += distances[from][to];
            }
        }
        return totalCost;
    }

    public static class Route {
        public List<Integer> nodes;
        public double remainingCapacity;

        public Route(double capacity) {
            nodes = new ArrayList<>();
            remainingCapacity = capacity;
        }

        @Override
        public String toString() {
            return "Route: " + nodes + ", Remaining Capacity: " + remainingCapacity;
        }
    }

    public static void main(String[] args) {
        int nodeCount = 6;
        double[][] distances = new double[nodeCount][nodeCount];
        double[] demands = new double[nodeCount];
        int vehicleCount = 2;
        double vehicleCapacity = 10.0;

        Random random = new Random(42);
        for (int i = 0; i < nodeCount; i++) {
            for (int j = i + 1; j < nodeCount; j++) {
                double distance = 10 + 90 * random.nextDouble();
                distances[i][j] = distance;
                distances[j][i] = distance;
            }
        }

        demands[0] = 0;
        for (int i = 1; i < nodeCount; i++) {
            demands[i] = 1 + 5 * random.nextDouble();
        }

        System.out.println("Solving VRP with " + (nodeCount - 1) + " customers and " + vehicleCount + " vehicles");

        AntColonyVRP aco = new AntColonyVRP(distances, demands, vehicleCount, vehicleCapacity);
        List<Route> bestSolution = aco.solve();

        System.out.println("Best solution found:");
        for (int i = 0; i < bestSolution.size(); i++) {
            System.out.println("Vehicle " + (i + 1) + ": " + bestSolution.get(i));
        }
    }
}
