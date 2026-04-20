package blob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Réseau résilient inspiré du blob (Physarum polycephalum).
 * Auto-organisation par renforcement/évaporation de connexions,
 * auto-réparation par détection de partitions et réactivation de liens.
 */
public class BlobInspiredNetwork {
    private static final Logger logger = Logger.getLogger(BlobInspiredNetwork.class.getName());

    private static final double EVAPORATION_RATE = 0.05;
    private static final double REINFORCEMENT_RATE = 0.1;
    private static final double EXPLORATION_RATE = 0.2;
    private static final int HEALTH_CHECK_INTERVAL = 5000;

    private final Map<String, Node> nodes = new ConcurrentHashMap<>();
    private final Map<String, Link> links = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public class Node {
        private final String id;
        private final Map<String, Link> connections = new HashMap<>();
        private boolean active = true;
        private final Map<String, Object> resources = new ConcurrentHashMap<>();
        private final List<Consumer<Node>> stateChangeListeners = new ArrayList<>();

        public Node(String id) { this.id = id; }

        public boolean sendMessage(String targetId, Object message) {
            if (!active) return false;

            Node targetNode = nodes.get(targetId);
            if (targetNode == null || !targetNode.isActive()) return false;

            List<Link> path = findPath(this, targetNode);
            if (path.isEmpty()) return false;

            boolean success = true;
            for (Link link : path) {
                if (!link.transmit(message)) {
                    success = false;
                    break;
                }
                link.reinforce();
            }

            return success;
        }

        public void addResource(String key, Object resource) { resources.put(key, resource); }
        public Object getResource(String key) { return resources.get(key); }

        public void setActive(boolean active) {
            if (this.active != active) {
                this.active = active;
                logger.info("Node " + id + " : " + (active ? "active" : "inactive"));
                for (Consumer<Node> listener : stateChangeListeners) listener.accept(this);
            }
        }

        public boolean isActive() { return active; }
        public void addStateChangeListener(Consumer<Node> listener) { stateChangeListeners.add(listener); }
        public String getId() { return id; }
        public Map<String, Link> getConnections() { return Collections.unmodifiableMap(connections); }

        void addConnection(Link link) {
            String targetId = link.getTarget(id);
            connections.put(targetId, link);
        }
    }

    public class Link {
        private final String id;
        final String nodeA;
        final String nodeB;
        private double strength;
        private double capacity;
        private double reliability;
        private boolean active = true;

        public Link(String id, String nodeA, String nodeB, double capacity, double reliability) {
            this.id = id;
            this.nodeA = nodeA;
            this.nodeB = nodeB;
            this.capacity = capacity;
            this.reliability = reliability;
            this.strength = 1.0;
        }

        public boolean transmit(Object message) {
            if (!active) return false;
            return Math.random() < reliability;
        }

        public void reinforce() { strength = Math.min(strength + REINFORCEMENT_RATE, 2.0); }
        public void evaporate() { strength = Math.max(strength - EVAPORATION_RATE, 0.1); }

        public void setActive(boolean active) {
            this.active = active;
            logger.info("Link " + id + " : " + (active ? "active" : "inactive"));
        }

        public boolean isActive() { return active; }
        public String getTarget(String sourceId) { return sourceId.equals(nodeA) ? nodeB : nodeA; }
        public double getStrength() { return strength; }
        public double getCapacity() { return capacity; }
        public double getReliability() { return reliability; }
        public String getId() { return id; }
    }

    public Node addNode(String id) {
        Node node = new Node(id);
        nodes.put(id, node);
        return node;
    }

    public Link connectNodes(String linkId, String nodeAId, String nodeBId, double capacity, double reliability) {
        Node nodeA = nodes.get(nodeAId);
        Node nodeB = nodes.get(nodeBId);

        if (nodeA == null || nodeB == null) {
            throw new IllegalArgumentException("Both nodes must exist");
        }

        Link link = new Link(linkId, nodeAId, nodeBId, capacity, reliability);
        links.put(linkId, link);

        nodeA.addConnection(link);
        nodeB.addConnection(link);

        return link;
    }

    public void start() {
        if (isRunning.compareAndSet(false, true)) {
            scheduler.scheduleAtFixedRate(() -> {
                for (Link link : links.values()) {
                    if (link.isActive()) link.evaporate();
                }
            }, 1, 1, TimeUnit.SECONDS);

            scheduler.scheduleAtFixedRate(this::performHealthCheck, 0, HEALTH_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
            logger.info("BlobInspiredNetwork started");
        }
    }

    public void stop() {
        if (isRunning.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow();
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("BlobInspiredNetwork stopped");
        }
    }

    private void performHealthCheck() {
        Map<String, Set<String>> reachableNodes = new HashMap<>();

        for (Node node : nodes.values()) {
            if (node.isActive()) reachableNodes.put(node.getId(), findReachableNodes(node));
        }

        List<Set<String>> partitions = identifyPartitions(reachableNodes);

        if (partitions.size() > 1) {
            logger.warning("Réseau partitionné en " + partitions.size() + " segments. Réparation...");
            attemptNetworkRepair(partitions);
        }
    }

    private Set<String> findReachableNodes(Node startNode) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        visited.add(startNode.getId());
        queue.add(startNode.getId());

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            Node current = nodes.get(currentId);

            if (current != null && current.isActive()) {
                for (Map.Entry<String, Link> entry : current.getConnections().entrySet()) {
                    String neighborId = entry.getKey();
                    Link link = entry.getValue();

                    if (link.isActive() && !visited.contains(neighborId)) {
                        Node neighbor = nodes.get(neighborId);
                        if (neighbor != null && neighbor.isActive()) {
                            visited.add(neighborId);
                            queue.add(neighborId);
                        }
                    }
                }
            }
        }

        return visited;
    }

    private List<Set<String>> identifyPartitions(Map<String, Set<String>> reachableNodes) {
        List<Set<String>> partitions = new ArrayList<>();
        Set<String> processedNodes = new HashSet<>();

        for (Map.Entry<String, Set<String>> entry : reachableNodes.entrySet()) {
            if (!processedNodes.contains(entry.getKey())) {
                Set<String> partition = entry.getValue();
                partitions.add(partition);
                processedNodes.addAll(partition);
            }
        }

        return partitions;
    }

    private void attemptNetworkRepair(List<Set<String>> partitions) {
        List<Link> potentialRepairLinks = new ArrayList<>();

        for (int i = 0; i < partitions.size(); i++) {
            Set<String> partition1 = partitions.get(i);

            for (int j = i + 1; j < partitions.size(); j++) {
                Set<String> partition2 = partitions.get(j);

                for (Link link : links.values()) {
                    boolean connectsPartitions =
                        (partition1.contains(link.nodeA) && partition2.contains(link.nodeB)) ||
                        (partition1.contains(link.nodeB) && partition2.contains(link.nodeA));

                    if (connectsPartitions && !link.isActive()) {
                        potentialRepairLinks.add(link);
                    }
                }
            }
        }

        if (!potentialRepairLinks.isEmpty()) {
            potentialRepairLinks.sort(Comparator.comparingDouble(Link::getStrength).reversed());
            Link bestLink = potentialRepairLinks.get(0);
            bestLink.setActive(true);
            logger.info("Réparation : lien réactivé " + bestLink.getId());
        } else {
            logger.warning("Aucun lien disponible entre partitions");
        }
    }

    private List<Link> findPath(Node source, Node target) {
        if (!source.isActive() || !target.isActive()) return Collections.emptyList();

        PriorityQueue<PathState> queue = new PriorityQueue<>(
            Comparator.comparingDouble(PathState::getPathStrength).reversed()
        );

        Set<String> visited = new HashSet<>();
        Map<String, PathState> bestPaths = new HashMap<>();

        PathState initial = new PathState(source.getId(), new ArrayList<>(), 1.0);
        queue.add(initial);
        bestPaths.put(source.getId(), initial);

        while (!queue.isEmpty()) {
            PathState current = queue.poll();
            String currentNodeId = current.nodeId;

            if (currentNodeId.equals(target.getId())) return current.path;

            if (visited.contains(currentNodeId)) continue;
            visited.add(currentNodeId);

            Node currentNode = nodes.get(currentNodeId);

            for (Map.Entry<String, Link> entry : currentNode.getConnections().entrySet()) {
                String neighborId = entry.getKey();
                Link link = entry.getValue();

                if (!link.isActive() || !nodes.get(neighborId).isActive()) continue;

                boolean explore = Math.random() < EXPLORATION_RATE;

                if (!visited.contains(neighborId) || explore) {
                    List<Link> newPath = new ArrayList<>(current.path);
                    newPath.add(link);

                    double newStrength = current.pathStrength * link.getStrength();
                    PathState neighborState = new PathState(neighborId, newPath, newStrength);

                    PathState existingPath = bestPaths.get(neighborId);
                    if (existingPath == null || neighborState.pathStrength > existingPath.pathStrength) {
                        bestPaths.put(neighborId, neighborState);
                        queue.add(neighborState);
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    private static class PathState {
        final String nodeId;
        final List<Link> path;
        final double pathStrength;

        PathState(String nodeId, List<Link> path, double pathStrength) {
            this.nodeId = nodeId;
            this.path = path;
            this.pathStrength = pathStrength;
        }

        double getPathStrength() { return pathStrength; }
    }

    public static void main(String[] args) throws InterruptedException {
        BlobInspiredNetwork network = new BlobInspiredNetwork();

        for (int i = 1; i <= 10; i++) network.addNode("node" + i);

        network.connectNodes("link1_2", "node1", "node2", 100, 0.99);
        network.connectNodes("link1_3", "node1", "node3", 100, 0.99);
        network.connectNodes("link2_4", "node2", "node4", 100, 0.99);
        network.connectNodes("link3_4", "node3", "node4", 100, 0.99);
        network.connectNodes("link3_5", "node3", "node5", 100, 0.99);
        network.connectNodes("link4_5", "node4", "node5", 100, 0.99);
        network.connectNodes("link5_6", "node5", "node6", 100, 0.99);
        network.connectNodes("link6_7", "node6", "node7", 100, 0.99);
        network.connectNodes("link6_8", "node6", "node8", 100, 0.99);
        network.connectNodes("link7_9", "node7", "node9", 100, 0.99);
        network.connectNodes("link8_9", "node8", "node9", 100, 0.99);
        network.connectNodes("link9_10", "node9", "node10", 100, 0.99);
        network.connectNodes("link2_5", "node2", "node5", 50, 0.95);
        network.connectNodes("link1_4", "node1", "node4", 50, 0.95);
        network.connectNodes("link5_8", "node5", "node8", 50, 0.95);
        network.connectNodes("link7_10", "node7", "node10", 50, 0.95);

        network.start();

        for (int i = 0; i < 20; i++) {
            network.nodes.get("node1").sendMessage("node10", "Message " + i);
            Thread.sleep(100);
        }

        logger.info("Panne de node4");
        network.nodes.get("node4").setActive(false);
        Thread.sleep(1000);

        for (int i = 20; i < 40; i++) {
            network.nodes.get("node1").sendMessage("node10", "Message " + i);
            Thread.sleep(100);
        }

        logger.info("Panne de link5_6");
        network.links.get("link5_6").setActive(false);
        Thread.sleep(HEALTH_CHECK_INTERVAL + 1000);

        for (int i = 40; i < 60; i++) {
            boolean success = network.nodes.get("node1").sendMessage("node10", "Message " + i);
            logger.info("Message " + i + ": " + (success ? "SUCCESS" : "FAILED"));
            Thread.sleep(100);
        }

        Thread.sleep(5000);
        network.stop();
    }
}
