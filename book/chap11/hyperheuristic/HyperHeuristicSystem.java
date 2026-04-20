package hyperheuristic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Système d'hyper-heuristique bio-inspiré : sélection adaptative
 * d'heuristiques de bas niveau selon les performances observées.
 * Imite la capacité du cerveau à sélectionner la "meilleure stratégie" selon le contexte.
 */
public class HyperHeuristicSystem {

    private final List<LowLevelHeuristic> heuristics = new ArrayList<>();
    private final Map<String, PerformanceRecord> performanceHistory = new HashMap<>();
    private final Random random = new Random();
    private double epsilon = 0.2;

    public void registerHeuristic(LowLevelHeuristic heuristic) {
        heuristics.add(heuristic);
        performanceHistory.put(heuristic.getName(), new PerformanceRecord());
    }

    /**
     * Résout un problème en choisissant itérativement la meilleure heuristique.
     */
    public Solution solve(Problem problem, int maxIterations) {
        Solution current = problem.initialSolution();
        Solution best = current;

        for (int iter = 0; iter < maxIterations; iter++) {
            // ε-greedy : explorer vs exploiter
            LowLevelHeuristic h;
            if (random.nextDouble() < epsilon) {
                h = heuristics.get(random.nextInt(heuristics.size()));
            } else {
                h = selectBestHeuristic();
            }

            long start = System.nanoTime();
            Solution newSol = h.apply(current, problem);
            long duration = System.nanoTime() - start;

            double improvement = problem.score(newSol) - problem.score(current);
            recordPerformance(h.getName(), improvement, duration);

            if (problem.score(newSol) > problem.score(current)) {
                current = newSol;
                if (problem.score(current) > problem.score(best)) best = current;
            }
        }

        return best;
    }

    private LowLevelHeuristic selectBestHeuristic() {
        double maxScore = Double.NEGATIVE_INFINITY;
        LowLevelHeuristic best = heuristics.get(0);
        for (LowLevelHeuristic h : heuristics) {
            double score = performanceHistory.get(h.getName()).averageImprovement();
            if (score > maxScore) {
                maxScore = score;
                best = h;
            }
        }
        return best;
    }

    private void recordPerformance(String name, double improvement, long durationNs) {
        PerformanceRecord rec = performanceHistory.get(name);
        rec.totalImprovement += improvement;
        rec.totalDurationNs += durationNs;
        rec.uses++;
    }

    public void printStats() {
        System.out.println("=== Performance des heuristiques ===");
        for (Map.Entry<String, PerformanceRecord> e : performanceHistory.entrySet()) {
            PerformanceRecord r = e.getValue();
            System.out.printf("%s: %d uses, avg improvement=%.3f%n",
                e.getKey(), r.uses, r.averageImprovement());
        }
    }

    public interface LowLevelHeuristic {
        String getName();
        Solution apply(Solution current, Problem problem);
    }

    public interface Problem {
        Solution initialSolution();
        double score(Solution s);
    }

    public interface Solution { }

    private static class PerformanceRecord {
        double totalImprovement = 0;
        long totalDurationNs = 0;
        int uses = 0;

        double averageImprovement() {
            return uses > 0 ? totalImprovement / uses : 0;
        }
    }

    public static void main(String[] args) {
        HyperHeuristicSystem hhs = new HyperHeuristicSystem();

        // Exemple avec 3 heuristiques fictives
        hhs.registerHeuristic(new LowLevelHeuristic() {
            @Override public String getName() { return "swap"; }
            @Override public Solution apply(Solution s, Problem p) { return s; }
        });
        hhs.registerHeuristic(new LowLevelHeuristic() {
            @Override public String getName() { return "insert"; }
            @Override public Solution apply(Solution s, Problem p) { return s; }
        });

        System.out.println("HyperHeuristicSystem initialisé avec " + hhs.heuristics.size() + " heuristiques");
    }
}
