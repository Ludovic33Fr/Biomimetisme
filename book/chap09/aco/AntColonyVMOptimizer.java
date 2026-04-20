package aco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Allocation de VMs aux serveurs d'un data center par ACO,
 * pour minimiser la consommation énergétique globale.
 */
public class AntColonyVMOptimizer {

    public static class VirtualMachine {
        private final String id;
        private final double cpuLoad;

        public VirtualMachine(String id, double cpuLoad) {
            this.id = id;
            this.cpuLoad = cpuLoad;
        }

        public String getId() { return id; }
        public double getCpuLoad() { return cpuLoad; }
    }

    public static class Server {
        private final int id;
        private final double capacity;
        private final double idlePower;
        private final double maxPower;

        public Server(int id, double capacity, double idlePower, double maxPower) {
            this.id = id;
            this.capacity = capacity;
            this.idlePower = idlePower;
            this.maxPower = maxPower;
        }

        public int getId() { return id; }
        public double getCapacity() { return capacity; }

        public double calculatePower(double currentLoad) {
            if (currentLoad <= 0) return 0;
            double utilization = Math.min(1.0, currentLoad / capacity);
            return idlePower + (maxPower - idlePower) * utilization;
        }
    }

    private final List<Server> servers;
    private final List<VirtualMachine> virtualMachines;
    private final int antCount;
    private final int iterations;
    private final double alpha;
    private final double beta;
    private final double evaporationRate;
    private final double[][] pheromones;

    public AntColonyVMOptimizer(List<Server> servers, List<VirtualMachine> vms,
                                int antCount, int iterations,
                                double alpha, double beta, double evaporationRate) {
        this.servers = servers;
        this.virtualMachines = vms;
        this.antCount = antCount;
        this.iterations = iterations;
        this.alpha = alpha;
        this.beta = beta;
        this.evaporationRate = evaporationRate;

        this.pheromones = new double[vms.size()][servers.size()];
        for (int i = 0; i < vms.size(); i++) {
            for (int j = 0; j < servers.size(); j++) {
                pheromones[i][j] = 1.0;
            }
        }
    }

    public VMAllocation optimize() {
        VMAllocation bestAllocation = null;
        double bestEnergy = Double.MAX_VALUE;

        for (int iter = 0; iter < iterations; iter++) {
            List<VMAllocation> antSolutions = new ArrayList<>();

            for (int ant = 0; ant < antCount; ant++) {
                VMAllocation allocation = buildSolution();
                if (allocation.isValid()) {
                    antSolutions.add(allocation);
                    double energy = allocation.calculateTotalEnergy();
                    if (energy < bestEnergy) {
                        bestEnergy = energy;
                        bestAllocation = allocation.copy();
                        System.out.printf("Iter %d, ant %d: %.2f W%n", iter, ant, bestEnergy);
                    }
                }
            }

            evaporatePheromones();
            for (VMAllocation allocation : antSolutions) depositPheromones(allocation);
        }

        return bestAllocation;
    }

    private VMAllocation buildSolution() {
        VMAllocation allocation = new VMAllocation(virtualMachines.size());

        List<Integer> vmIndices = IntStream.range(0, virtualMachines.size())
                                          .boxed()
                                          .collect(Collectors.toList());
        Collections.shuffle(vmIndices);

        for (int vmIdx : vmIndices) {
            VirtualMachine vm = virtualMachines.get(vmIdx);

            double[] probabilities = new double[servers.size()];
            double totalProbability = 0.0;

            for (int serverId = 0; serverId < servers.size(); serverId++) {
                Server server = servers.get(serverId);
                if (allocation.canAllocate(vm, server)) {
                    double pheromone = Math.pow(pheromones[vmIdx][serverId], alpha);
                    double heuristic = Math.pow(calculateHeuristic(vm, server, allocation), beta);
                    probabilities[serverId] = pheromone * heuristic;
                    totalProbability += probabilities[serverId];
                }
            }

            int selectedServer = -1;
            if (totalProbability > 0) {
                double rand = Math.random() * totalProbability;
                double cumul = 0.0;
                for (int serverId = 0; serverId < servers.size(); serverId++) {
                    cumul += probabilities[serverId];
                    if (rand <= cumul) { selectedServer = serverId; break; }
                }
            }

            if (selectedServer == -1) selectedServer = (int) (Math.random() * servers.size());

            allocation.allocate(vmIdx, selectedServer);
        }

        return allocation;
    }

    private double calculateHeuristic(VirtualMachine vm, Server server, VMAllocation allocation) {
        double currentLoad = allocation.getServerLoad(server.getId());
        double currentPower = server.calculatePower(currentLoad);
        double newLoad = currentLoad + vm.getCpuLoad();
        double newPower = server.calculatePower(newLoad);

        double powerIncrease = newPower - currentPower;
        return powerIncrease <= 0 ? 100.0 : 1.0 / powerIncrease;
    }

    private void evaporatePheromones() {
        for (int i = 0; i < virtualMachines.size(); i++) {
            for (int j = 0; j < servers.size(); j++) {
                pheromones[i][j] *= (1 - evaporationRate);
            }
        }
    }

    private void depositPheromones(VMAllocation allocation) {
        double quality = 1.0 / allocation.calculateTotalEnergy();

        for (int vmIdx = 0; vmIdx < virtualMachines.size(); vmIdx++) {
            int serverId = allocation.getServerForVM(vmIdx);
            pheromones[vmIdx][serverId] += quality;
        }
    }

    public class VMAllocation {
        private final int[] allocation;
        private final Map<Integer, Double> serverLoads = new HashMap<>();

        public VMAllocation(int vmCount) {
            allocation = new int[vmCount];
            Arrays.fill(allocation, -1);
            for (int i = 0; i < servers.size(); i++) serverLoads.put(i, 0.0);
        }

        public void allocate(int vmIdx, int serverId) {
            if (allocation[vmIdx] != -1) {
                double oldLoad = serverLoads.get(allocation[vmIdx]);
                serverLoads.put(allocation[vmIdx], oldLoad - virtualMachines.get(vmIdx).getCpuLoad());
            }

            allocation[vmIdx] = serverId;
            double newLoad = serverLoads.get(serverId) + virtualMachines.get(vmIdx).getCpuLoad();
            serverLoads.put(serverId, newLoad);
        }

        public boolean canAllocate(VirtualMachine vm, Server server) {
            double currentLoad = serverLoads.getOrDefault(server.getId(), 0.0);
            return currentLoad + vm.getCpuLoad() <= server.getCapacity();
        }

        public double getServerLoad(int serverId) { return serverLoads.getOrDefault(serverId, 0.0); }
        public int getServerForVM(int vmIdx) { return allocation[vmIdx]; }

        public boolean isValid() {
            for (int serverId : allocation) if (serverId == -1) return false;
            for (int serverId = 0; serverId < servers.size(); serverId++) {
                if (serverLoads.get(serverId) > servers.get(serverId).getCapacity()) return false;
            }
            return true;
        }

        public double calculateTotalEnergy() {
            double total = 0.0;
            for (int serverId = 0; serverId < servers.size(); serverId++) {
                double load = serverLoads.get(serverId);
                if (load > 0) total += servers.get(serverId).calculatePower(load);
            }
            return total;
        }

        public VMAllocation copy() {
            VMAllocation copy = new VMAllocation(allocation.length);
            for (int i = 0; i < allocation.length; i++) copy.allocate(i, allocation[i]);
            return copy;
        }
    }

    public static void main(String[] args) {
        List<Server> servers = IntStream.range(0, 8)
                .mapToObj(i -> new Server(i, 100, 50, 200))
                .collect(Collectors.toList());

        List<VirtualMachine> vms = IntStream.range(0, 30)
                .mapToObj(i -> new VirtualMachine("VM-" + i, 10 + Math.random() * 30))
                .collect(Collectors.toList());

        AntColonyVMOptimizer optimizer = new AntColonyVMOptimizer(servers, vms, 20, 50, 1.0, 2.0, 0.1);
        VMAllocation best = optimizer.optimize();

        System.out.printf("Énergie finale: %.2f W%n", best.calculateTotalEnergy());
    }
}
