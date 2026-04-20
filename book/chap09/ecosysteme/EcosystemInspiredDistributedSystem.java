package ecosysteme;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Système distribué bio-inspiré qui optimise la répartition des tâches
 * pour minimiser la consommation énergétique globale.
 * Inspiré des niches écologiques et de la dynamique des populations.
 */
public class EcosystemInspiredDistributedSystem {

    private List<ComputeNode> nodes;
    private Queue<Task> taskQueue;
    private final ScheduledExecutorService scheduler;

    public EcosystemInspiredDistributedSystem(int nodeCount) {
        this.nodes = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) nodes.add(new ComputeNode("Node-" + i));

        this.taskQueue = new ConcurrentLinkedQueue<>();
        this.scheduler = Executors.newScheduledThreadPool(1);

        startBioInspiredLoadBalancing();
    }

    private void startBioInspiredLoadBalancing() {
        scheduler.scheduleAtFixedRate(() -> {
            redistributeTasks();
            optimizeNodeConfigurations();
            manageNodeLifecycle();
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void redistributeTasks() {
        System.out.println("Redistribution des tâches...");
        nodes.sort(Comparator.comparingDouble(ComputeNode::getEnergyEfficiency).reversed());

        List<Task> allTasks = new ArrayList<>();
        for (ComputeNode node : nodes) allTasks.addAll(node.unloadPendingTasks());

        Task task;
        while ((task = taskQueue.poll()) != null) allTasks.add(task);

        allTasks.sort(Comparator.comparingInt(Task::getPriority).reversed());

        for (Task t : allTasks) {
            boolean assigned = false;
            for (ComputeNode node : nodes) {
                if (node.isActive() && node.canAcceptTask(t)) {
                    node.assignTask(t);
                    assigned = true;
                    break;
                }
            }
            if (!assigned) taskQueue.add(t);
        }
    }

    private void optimizeNodeConfigurations() {
        for (ComputeNode node : nodes) {
            if (node.isActive()) {
                double load = node.getCurrentLoad();
                if (load < 0.3) node.setEnergyProfile(ComputeNode.EnergyProfile.ECO);
                else if (load < 0.7) node.setEnergyProfile(ComputeNode.EnergyProfile.BALANCED);
                else node.setEnergyProfile(ComputeNode.EnergyProfile.PERFORMANCE);
            }
        }
    }

    private void manageNodeLifecycle() {
        double totalCapacity = nodes.size() * 100.0;
        double totalLoad = nodes.stream()
                .filter(ComputeNode::isActive)
                .mapToDouble(node -> node.getCurrentLoad() * 100.0)
                .sum();

        double systemLoad = totalLoad / totalCapacity;
        System.out.println("Charge globale: " + String.format("%.2f%%", systemLoad * 100));

        if (systemLoad > 0.8) activateInactiveNodes(2);
        else if (systemLoad < 0.3) deactivateUnderutilizedNodes(1);
    }

    private void activateInactiveNodes(int count) {
        int activated = 0;
        for (ComputeNode node : nodes) {
            if (!node.isActive()) {
                node.activate();
                System.out.println("Activé: " + node.getId());
                activated++;
                if (activated >= count) break;
            }
        }
    }

    private void deactivateUnderutilizedNodes(int count) {
        List<ComputeNode> activeNodes = nodes.stream()
                .filter(ComputeNode::isActive)
                .sorted(Comparator.comparingDouble(ComputeNode::getCurrentLoad))
                .collect(Collectors.toList());

        if (activeNodes.size() <= 1) return;

        int deactivated = 0;
        for (ComputeNode node : activeNodes) {
            if (node.getCurrentLoad() < 0.2) {
                List<Task> tasks = node.unloadAllTasks();
                for (Task task : tasks) taskQueue.add(task);
                node.deactivate();
                System.out.println("Désactivé: " + node.getId());
                deactivated++;
                if (deactivated >= count) break;
            }
        }
    }

    public void submitTask(Task task) {
        taskQueue.add(task);
        System.out.println("Tâche: " + task.getId());
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) scheduler.shutdownNow();
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static class ComputeNode {
        public enum EnergyProfile { ECO, BALANCED, PERFORMANCE }

        private final String id;
        private boolean active;
        private final List<Task> assignedTasks;
        private EnergyProfile energyProfile;
        private final Random random;

        public ComputeNode(String id) {
            this.id = id;
            this.active = true;
            this.assignedTasks = new ArrayList<>();
            this.energyProfile = EnergyProfile.BALANCED;
            this.random = new Random();
        }

        public double getEnergyEfficiency() {
            double base = random.nextDouble() * 0.5 + 0.5;
            switch (energyProfile) {
                case ECO: return base * 1.2;
                case PERFORMANCE: return base * 0.8;
                default: return base;
            }
        }

        public double getCurrentLoad() {
            return Math.min(1.0, assignedTasks.size() / 10.0);
        }

        public boolean canAcceptTask(Task task) {
            return active && getCurrentLoad() < 0.9;
        }

        public void assignTask(Task task) {
            if (active) assignedTasks.add(task);
        }

        public List<Task> unloadPendingTasks() {
            List<Task> pending = assignedTasks.stream()
                    .filter(t -> !t.isStarted()).collect(Collectors.toList());
            assignedTasks.removeAll(pending);
            return pending;
        }

        public List<Task> unloadAllTasks() {
            List<Task> all = new ArrayList<>(assignedTasks);
            assignedTasks.clear();
            return all;
        }

        public void activate() { active = true; }
        public void deactivate() { active = false; }
        public boolean isActive() { return active; }

        public void setEnergyProfile(EnergyProfile profile) {
            if (this.energyProfile != profile) {
                this.energyProfile = profile;
                System.out.println("Node " + id + " → profil " + profile);
            }
        }

        public String getId() { return id; }
    }

    public static class Task {
        private final String id;
        private final int priority;
        private boolean started;

        public Task(String id, int priority) {
            this.id = id;
            this.priority = priority;
            this.started = false;
        }

        public String getId() { return id; }
        public int getPriority() { return priority; }
        public boolean isStarted() { return started; }
        public void start() { started = true; }
    }

    public static void main(String[] args) throws InterruptedException {
        EcosystemInspiredDistributedSystem system = new EcosystemInspiredDistributedSystem(5);

        for (int i = 1; i <= 20; i++) {
            system.submitTask(new Task("Task-" + i, i % 3 + 1));
            Thread.sleep(500);
        }

        Thread.sleep(30000);
        system.shutdown();
    }
}
