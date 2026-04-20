package loadbalancer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load balancer bio-inspiré combinant phéromones (ACO) et adaptation
 * homéostatique. Distribue les requêtes vers les backends les plus performants,
 * en tenant compte de la charge et de la latence.
 */
public class BioInspiredLoadBalancer {

    private final List<Backend> backends = new ArrayList<>();
    private final Map<String, Double> pheromones = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Random random = new Random();

    private final double evaporationRate = 0.05;
    private final double initialPheromone = 1.0;
    private final double alpha = 1.0;  // importance des phéromones
    private final double beta = 2.0;   // importance de la performance

    public BioInspiredLoadBalancer() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        startPheromoneEvaporation();
    }

    public void registerBackend(Backend backend) {
        backends.add(backend);
        pheromones.put(backend.getId(), initialPheromone);
        System.out.println("Backend enregistré: " + backend.getId());
    }

    private void startPheromoneEvaporation() {
        scheduler.scheduleAtFixedRate(() -> {
            for (String id : pheromones.keySet()) {
                pheromones.merge(id, 0.0, (old, dummy) -> Math.max(0.1, old * (1 - evaporationRate)));
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Sélectionne un backend selon une probabilité pondérée phéromones × performance.
     */
    public Backend routeRequest() {
        double total = 0;
        double[] weights = new double[backends.size()];

        for (int i = 0; i < backends.size(); i++) {
            Backend b = backends.get(i);
            if (!b.isHealthy()) { weights[i] = 0; continue; }

            double pher = pheromones.getOrDefault(b.getId(), initialPheromone);
            double perf = 1.0 / Math.max(1.0, b.getAverageLatencyMs());
            weights[i] = Math.pow(pher, alpha) * Math.pow(perf, beta) * (1 - b.getLoad());
            total += weights[i];
        }

        if (total <= 0) return backends.get(random.nextInt(backends.size()));

        double r = random.nextDouble() * total;
        double cumul = 0;
        for (int i = 0; i < backends.size(); i++) {
            cumul += weights[i];
            if (r <= cumul) return backends.get(i);
        }

        return backends.get(backends.size() - 1);
    }

    /**
     * Feedback : le backend renforce ses phéromones selon la qualité de la réponse.
     */
    public void recordFeedback(Backend backend, long latencyMs, boolean success) {
        if (success) {
            double reward = 1000.0 / (latencyMs + 100);
            pheromones.merge(backend.getId(), reward, Double::sum);
        } else {
            pheromones.merge(backend.getId(), -0.2, Double::sum);
        }
        backend.recordLatency(latencyMs);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class Backend {
        private final String id;
        private final AtomicInteger activeRequests = new AtomicInteger(0);
        private final int capacity;
        private double avgLatencyMs = 10;
        private boolean healthy = true;

        public Backend(String id, int capacity) {
            this.id = id;
            this.capacity = capacity;
        }

        public String getId() { return id; }
        public double getLoad() { return (double) activeRequests.get() / capacity; }
        public double getAverageLatencyMs() { return avgLatencyMs; }
        public boolean isHealthy() { return healthy; }

        public void incrementActive() { activeRequests.incrementAndGet(); }
        public void decrementActive() { activeRequests.decrementAndGet(); }

        public void recordLatency(long latency) {
            avgLatencyMs = 0.9 * avgLatencyMs + 0.1 * latency;
        }

        public void setHealthy(boolean h) { this.healthy = h; }
    }

    public static void main(String[] args) throws InterruptedException {
        BioInspiredLoadBalancer lb = new BioInspiredLoadBalancer();
        lb.registerBackend(new Backend("api-1", 100));
        lb.registerBackend(new Backend("api-2", 100));
        lb.registerBackend(new Backend("api-3", 100));

        // Simuler 100 requêtes
        Random random = new Random();
        for (int i = 0; i < 100; i++) {
            Backend target = lb.routeRequest();
            target.incrementActive();
            long latency = 5 + random.nextInt(50);
            boolean success = random.nextDouble() < 0.95;
            lb.recordFeedback(target, latency, success);
            target.decrementActive();
        }

        System.out.println("=== État final ===");
        lb.pheromones.forEach((id, value) -> System.out.printf("%s : pheromones=%.3f%n", id, value));

        lb.shutdown();
    }
}
