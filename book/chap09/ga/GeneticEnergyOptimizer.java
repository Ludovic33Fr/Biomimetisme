package ga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Optimisation de l'allocation de VMs aux serveurs physiques
 * par algorithme génétique, pour minimiser la consommation énergétique.
 */
public class GeneticEnergyOptimizer {

    private static final int POPULATION_SIZE = 100;
    private static final int MAX_GENERATIONS = 50;
    private static final double MUTATION_RATE = 0.05;
    private static final double CROSSOVER_RATE = 0.8;
    private static final int TOURNAMENT_SIZE = 5;

    private final List<VirtualMachine> vms;
    private final List<PhysicalMachine> pms;
    private final Random random = new Random();

    public static class VirtualMachine {
        public final String id;
        public final double requiredCpu;
        public final double requiredMemory;

        public VirtualMachine(String id, double cpu, double memory) {
            this.id = id;
            this.requiredCpu = cpu;
            this.requiredMemory = memory;
        }
    }

    public static class PhysicalMachine {
        public final String id;
        public final double capacityCpu;
        public final double capacityMemory;
        public final double idlePower;
        public final double maxPower;

        public PhysicalMachine(String id, double cpu, double memory, double idle, double max) {
            this.id = id;
            this.capacityCpu = cpu;
            this.capacityMemory = memory;
            this.idlePower = idle;
            this.maxPower = max;
        }

        public double calculatePower(double currentCpuLoad) {
            if (currentCpuLoad <= 0) return 0;
            double utilization = Math.min(1.0, currentCpuLoad / capacityCpu);
            return idlePower + (maxPower - idlePower) * utilization;
        }
    }

    public static class AllocationSolution implements Comparable<AllocationSolution> {
        public final int[] chromosome;
        public double fitness = -1.0;
        public double totalPowerConsumption = -1.0;
        public boolean constraintsViolated = false;

        public AllocationSolution(int numVMs) { chromosome = new int[numVMs]; }

        public AllocationSolution copy() {
            AllocationSolution clone = new AllocationSolution(chromosome.length);
            System.arraycopy(this.chromosome, 0, clone.chromosome, 0, chromosome.length);
            clone.fitness = this.fitness;
            clone.totalPowerConsumption = this.totalPowerConsumption;
            clone.constraintsViolated = this.constraintsViolated;
            return clone;
        }

        @Override
        public int compareTo(AllocationSolution other) {
            return Double.compare(this.fitness, other.fitness);
        }
    }

    public GeneticEnergyOptimizer(List<VirtualMachine> vms, List<PhysicalMachine> pms) {
        this.vms = vms;
        this.pms = pms;
    }

    public AllocationSolution optimizeAllocation() {
        List<AllocationSolution> population = initializePopulation();
        evaluatePopulation(population);

        AllocationSolution bestSolution = Collections.max(population);
        System.out.printf("Gen 0: Fitness=%.4f, Power=%.2f W%n",
                bestSolution.fitness, bestSolution.totalPowerConsumption);

        for (int generation = 1; generation <= MAX_GENERATIONS; generation++) {
            List<AllocationSolution> newPopulation = new ArrayList<>();
            newPopulation.add(bestSolution.copy());

            while (newPopulation.size() < POPULATION_SIZE) {
                AllocationSolution parent1 = selectParent(population);
                AllocationSolution parent2 = selectParent(population);

                AllocationSolution child;
                if (random.nextDouble() < CROSSOVER_RATE) {
                    child = crossover(parent1, parent2);
                } else {
                    child = parent1.copy();
                }

                mutate(child);
                newPopulation.add(child);
            }

            evaluatePopulation(newPopulation);
            population = newPopulation;
            bestSolution = Collections.max(population);

            System.out.printf("Gen %d: Fitness=%.4f, Power=%.2f W%n",
                    generation, bestSolution.fitness, bestSolution.totalPowerConsumption);
        }

        return bestSolution;
    }

    private List<AllocationSolution> initializePopulation() {
        List<AllocationSolution> population = new ArrayList<>(POPULATION_SIZE);
        for (int i = 0; i < POPULATION_SIZE; i++) {
            AllocationSolution solution = new AllocationSolution(vms.size());
            for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
                solution.chromosome[vmIndex] = random.nextInt(pms.size());
            }
            population.add(solution);
        }
        return population;
    }

    private void evaluatePopulation(List<AllocationSolution> population) {
        for (AllocationSolution solution : population) calculateFitness(solution);
    }

    private void calculateFitness(AllocationSolution solution) {
        double[] pmCpuLoad = new double[pms.size()];
        double[] pmMemoryLoad = new double[pms.size()];
        boolean constraintsViolated = false;

        for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
            int pmIndex = solution.chromosome[vmIndex];
            VirtualMachine vm = vms.get(vmIndex);
            pmCpuLoad[pmIndex] += vm.requiredCpu;
            pmMemoryLoad[pmIndex] += vm.requiredMemory;
        }

        double totalPower = 0;
        int activePMs = 0;
        for (int pmIndex = 0; pmIndex < pms.size(); pmIndex++) {
            PhysicalMachine pm = pms.get(pmIndex);
            if (pmCpuLoad[pmIndex] > 0) {
                activePMs++;
                if (pmCpuLoad[pmIndex] > pm.capacityCpu || pmMemoryLoad[pmIndex] > pm.capacityMemory) {
                    constraintsViolated = true;
                }
                totalPower += pm.calculatePower(pmCpuLoad[pmIndex]);
            }
        }

        solution.totalPowerConsumption = totalPower;
        solution.constraintsViolated = constraintsViolated;

        double penalty = constraintsViolated ? 10.0 : 1.0;
        double serverPenalty = 1.0 + (double) activePMs / pms.size() * 0.1;

        solution.fitness = totalPower > 0 ? 1.0 / (totalPower * penalty * serverPenalty) : 0;
    }

    private AllocationSolution selectParent(List<AllocationSolution> population) {
        AllocationSolution bestInTournament = null;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            AllocationSolution candidate = population.get(random.nextInt(population.size()));
            if (bestInTournament == null || candidate.fitness > bestInTournament.fitness) {
                bestInTournament = candidate;
            }
        }
        return bestInTournament;
    }

    private AllocationSolution crossover(AllocationSolution parent1, AllocationSolution parent2) {
        AllocationSolution child = new AllocationSolution(vms.size());
        for (int i = 0; i < vms.size(); i++) {
            child.chromosome[i] = random.nextDouble() < 0.5 ? parent1.chromosome[i] : parent2.chromosome[i];
        }
        return child;
    }

    private void mutate(AllocationSolution solution) {
        for (int i = 0; i < vms.size(); i++) {
            if (random.nextDouble() < MUTATION_RATE) {
                solution.chromosome[i] = random.nextInt(pms.size());
            }
        }
    }

    public static void main(String[] args) {
        List<VirtualMachine> vms = IntStream.range(0, 50)
                .mapToObj(i -> new VirtualMachine("VM-" + i, 10 + Math.random() * 40, 1 + Math.random() * 7))
                .collect(Collectors.toList());

        List<PhysicalMachine> pms = IntStream.range(0, 10)
                .mapToObj(i -> new PhysicalMachine("PM-" + i, 100, 64, 50, 200))
                .collect(Collectors.toList());

        GeneticEnergyOptimizer optimizer = new GeneticEnergyOptimizer(vms, pms);
        AllocationSolution best = optimizer.optimizeAllocation();

        System.out.printf("%nFitness: %.6f, Power: %.2f W%n", best.fitness, best.totalPowerConsumption);

        Map<Integer, List<Integer>> map = new HashMap<>();
        for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
            map.computeIfAbsent(best.chromosome[vmIndex], k -> new ArrayList<>()).add(vmIndex);
        }

        for (Map.Entry<Integer, List<Integer>> entry : map.entrySet()) {
            System.out.printf("PM-%d: %d VMs%n", entry.getKey(), entry.getValue().size());
        }
    }
}
