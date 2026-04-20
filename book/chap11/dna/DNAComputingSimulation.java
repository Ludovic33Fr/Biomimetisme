package dna;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simulation d'ADN computing sur le problème du chemin hamiltonien.
 * Chaque "brin d'ADN" représente un chemin potentiel ; les opérations
 * moléculaires (hybridation, ligation, amplification, sélection) sont
 * simulées par des opérations ensemblistes.
 *
 * Inspiré des expériences pionnières d'Adleman (1994).
 */
public class DNAComputingSimulation {

    private final int numCities;
    private final boolean[][] adjacency;
    private final int startCity;
    private final int endCity;

    public DNAComputingSimulation(int numCities, boolean[][] adjacency, int start, int end) {
        this.numCities = numCities;
        this.adjacency = adjacency;
        this.startCity = start;
        this.endCity = end;
    }

    /**
     * Étape 1 : génération massive de brins (tous les chemins possibles de longueur variable).
     * Analogue à l'amplification par PCR d'une banque de séquences aléatoires.
     */
    public List<List<Integer>> generateRandomStrands(int count) {
        Random random = new Random();
        List<List<Integer>> strands = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            List<Integer> path = new ArrayList<>();
            int length = 1 + random.nextInt(numCities);
            for (int j = 0; j < length; j++) {
                path.add(random.nextInt(numCities));
            }
            strands.add(path);
        }
        return strands;
    }

    /**
     * Étape 2 : filtre les chemins par début/fin (hybridation avec des amorces).
     */
    public List<List<Integer>> filterByEndpoints(List<List<Integer>> strands) {
        return strands.stream()
                .filter(s -> !s.isEmpty() && s.get(0) == startCity && s.get(s.size() - 1) == endCity)
                .collect(Collectors.toList());
    }

    /**
     * Étape 3 : filtre les chemins de bonne longueur (gel électrophorèse).
     */
    public List<List<Integer>> filterByLength(List<List<Integer>> strands, int targetLength) {
        return strands.stream()
                .filter(s -> s.size() == targetLength)
                .collect(Collectors.toList());
    }

    /**
     * Étape 4 : filtre les chemins dont toutes les arêtes sont valides.
     */
    public List<List<Integer>> filterByValidEdges(List<List<Integer>> strands) {
        return strands.stream()
                .filter(this::hasValidEdges)
                .collect(Collectors.toList());
    }

    /**
     * Étape 5 : filtre les chemins qui visitent toutes les villes exactement une fois.
     */
    public List<List<Integer>> filterByCompleteness(List<List<Integer>> strands) {
        return strands.stream()
                .filter(this::visitsAllCities)
                .collect(Collectors.toList());
    }

    private boolean hasValidEdges(List<Integer> path) {
        for (int i = 0; i < path.size() - 1; i++) {
            if (!adjacency[path.get(i)][path.get(i + 1)]) return false;
        }
        return true;
    }

    private boolean visitsAllCities(List<Integer> path) {
        Set<Integer> visited = new HashSet<>(path);
        return visited.size() == numCities;
    }

    /**
     * Pipeline complet à la Adleman.
     */
    public List<List<Integer>> solveHamiltonianPath(int initialStrandCount) {
        System.out.println("1. Génération de " + initialStrandCount + " brins aléatoires...");
        List<List<Integer>> strands = generateRandomStrands(initialStrandCount);

        System.out.println("2. Filtre endpoints (début=" + startCity + ", fin=" + endCity + ")...");
        strands = filterByEndpoints(strands);
        System.out.println("   → " + strands.size() + " brins");

        System.out.println("3. Filtre longueur (" + numCities + " villes)...");
        strands = filterByLength(strands, numCities);
        System.out.println("   → " + strands.size() + " brins");

        System.out.println("4. Filtre arêtes valides...");
        strands = filterByValidEdges(strands);
        System.out.println("   → " + strands.size() + " brins");

        System.out.println("5. Filtre complétude (toutes les villes visitées)...");
        strands = filterByCompleteness(strands);
        System.out.println("   → " + strands.size() + " chemins hamiltoniens");

        return strands;
    }

    public static void main(String[] args) {
        // Graphe d'Adleman à 7 villes
        int n = 7;
        boolean[][] graph = new boolean[n][n];
        int[][] edges = {{0,1},{0,3},{1,2},{1,3},{2,6},{3,4},{3,5},{4,6},{5,6}};
        for (int[] e : edges) graph[e[0]][e[1]] = true;

        DNAComputingSimulation dna = new DNAComputingSimulation(n, graph, 0, 6);
        List<List<Integer>> solutions = dna.solveHamiltonianPath(100000);

        System.out.println("\nChemins hamiltoniens trouvés :");
        for (List<Integer> path : solutions.stream().distinct().collect(Collectors.toList())) {
            System.out.println("  " + path);
        }
    }
}
