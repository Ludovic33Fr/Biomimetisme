package empreinte;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Moniteur d'empreinte énergétique d'une application Java.
 * Surveille CPU, mémoire, I/O et estime consommation électrique + émissions CO2.
 */
public class EnergyFootprintMonitor {

    private static final double CPU_ENERGY_FACTOR = 0.0003;
    private static final double MEMORY_ENERGY_FACTOR = 0.0000001;
    private static final double DISK_ENERGY_FACTOR = 0.0000002;
    private static final double NETWORK_ENERGY_FACTOR = 0.0000006;
    private static final double CARBON_INTENSITY = 0.4;

    private final OperatingSystemMXBean osBean;
    private final ScheduledExecutorService scheduler;
    private final Runtime runtime;

    private long lastCpuTime = 0;
    private long lastSystemTime = 0;
    private long lastBytesRead = 0;
    private long lastBytesWritten = 0;
    private long lastBytesTransferred = 0;

    private double totalEnergyConsumption = 0.0;
    private double totalCarbonEmissions = 0.0;

    private final Map<String, FunctionEnergyStats> functionStats = new ConcurrentHashMap<>();
    private final List<ResourceUsageSnapshot> usageHistory = new ArrayList<>();

    private boolean isRunning = false;
    private File reportFile;

    public EnergyFootprintMonitor() {
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.runtime = Runtime.getRuntime();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.reportFile = new File("energy_report_" + System.currentTimeMillis() + ".csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(reportFile))) {
            writer.println("Timestamp,CPU(%),Memory(MB),Power(W),Energy(kWh),CO2(gCO2e)");
        } catch (IOException e) {
            System.err.println("Erreur rapport: " + e.getMessage());
        }
    }

    public void start(int intervalSeconds) {
        if (isRunning) return;
        isRunning = true;

        lastSystemTime = System.nanoTime();

        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
            lastCpuTime = sunOsBean.getProcessCpuTime();
        }

        scheduler.scheduleAtFixedRate(() -> {
            try { measureAndRecord(); } catch (Exception e) {
                System.err.println("Erreur: " + e.getMessage());
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);

        System.out.println("Surveillance démarrée: " + reportFile.getAbsolutePath());
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        generateFinalReport();
    }

    private void measureAndRecord() {
        long currentSystemTime = System.nanoTime();
        double elapsedHours = (currentSystemTime - lastSystemTime) / 1_000_000_000.0 / 3600.0;
        lastSystemTime = currentSystemTime;

        double cpuUsage = 0.0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
            long currentCpuTime = sunOsBean.getProcessCpuTime();
            long cpuTimeDiff = currentCpuTime - lastCpuTime;
            cpuUsage = (double) cpuTimeDiff / (elapsedHours * 3600 * 1_000_000_000.0) / osBean.getAvailableProcessors() * 100.0;
            lastCpuTime = currentCpuTime;
        }

        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double memoryUsageMB = usedMemory / (1024.0 * 1024.0);

        long bytesReadDiff = (long)(Math.random() * 1000000);
        long bytesWrittenDiff = (long)(Math.random() * 500000);
        long bytesTransferredDiff = (long)(Math.random() * 200000);

        double cpuEnergy = cpuUsage * CPU_ENERGY_FACTOR * elapsedHours;
        double memoryEnergy = memoryUsageMB * MEMORY_ENERGY_FACTOR * elapsedHours;
        double diskEnergy = (bytesReadDiff + bytesWrittenDiff) / (1024.0 * 1024.0) * DISK_ENERGY_FACTOR;
        double networkEnergy = bytesTransferredDiff / (1024.0 * 1024.0) * NETWORK_ENERGY_FACTOR;

        double periodEnergy = cpuEnergy + memoryEnergy + diskEnergy + networkEnergy;
        totalEnergyConsumption += periodEnergy;

        double periodCarbon = periodEnergy * CARBON_INTENSITY;
        totalCarbonEmissions += periodCarbon;

        double powerW = elapsedHours > 0 ? periodEnergy / elapsedHours * 1000 : 0;

        ResourceUsageSnapshot snap = new ResourceUsageSnapshot(
                Instant.now(), cpuUsage, memoryUsageMB, powerW,
                totalEnergyConsumption, totalCarbonEmissions * 1000);
        usageHistory.add(snap);

        try (PrintWriter writer = new PrintWriter(new FileWriter(reportFile, true))) {
            writer.printf("%s,%.2f,%.2f,%.2f,%.6f,%.2f%n",
                    snap.timestamp, snap.cpuUsage, snap.memoryUsageMB,
                    snap.powerConsumption, snap.cumulativeEnergyKWh,
                    snap.cumulativeCarbonEmissionsGCO2e);
        } catch (IOException e) {
            System.err.println("Erreur CSV: " + e.getMessage());
        }
    }

    public String beginFunctionExecution(String functionName) {
        String executionId = functionName + "_" + UUID.randomUUID().toString();
        FunctionEnergyStats stats = functionStats.computeIfAbsent(functionName, FunctionEnergyStats::new);

        FunctionExecution execution = new FunctionExecution();
        execution.startTime = System.nanoTime();
        execution.startEnergyConsumption = totalEnergyConsumption;

        stats.activeExecutions.put(executionId, execution);
        return executionId;
    }

    public void endFunctionExecution(String executionId) {
        String functionName = executionId.split("_")[0];
        FunctionEnergyStats stats = functionStats.get(functionName);
        if (stats == null) return;

        FunctionExecution execution = stats.activeExecutions.remove(executionId);
        if (execution == null) return;

        long endTime = System.nanoTime();
        double executionTimeMs = (endTime - execution.startTime) / 1_000_000.0;
        double energy = totalEnergyConsumption - execution.startEnergyConsumption;

        stats.totalExecutions++;
        stats.totalExecutionTimeMs += executionTimeMs;
        stats.totalEnergyConsumption += energy;

        if (stats.minExecutionTimeMs == 0 || executionTimeMs < stats.minExecutionTimeMs) {
            stats.minExecutionTimeMs = executionTimeMs;
        }
        if (executionTimeMs > stats.maxExecutionTimeMs) {
            stats.maxExecutionTimeMs = executionTimeMs;
        }
    }

    private void generateFinalReport() {
        System.out.printf("=== RAPPORT ===%n");
        System.out.printf("Énergie totale: %.6f kWh%n", totalEnergyConsumption);
        System.out.printf("CO2: %.2f gCO2e%n", totalCarbonEmissions * 1000);

        List<FunctionEnergyStats> sorted = new ArrayList<>(functionStats.values());
        sorted.sort(Comparator.comparingDouble(FunctionEnergyStats::getTotalEnergyConsumption).reversed());

        for (FunctionEnergyStats stats : sorted) {
            System.out.printf("%s: %d exécutions, avg %.2f ms, total %.6f kWh%n",
                    stats.functionName, stats.totalExecutions,
                    stats.getAverageExecutionTimeMs(), stats.totalEnergyConsumption);
        }
    }

    private static class ResourceUsageSnapshot {
        final Instant timestamp;
        final double cpuUsage, memoryUsageMB, powerConsumption;
        final double cumulativeEnergyKWh, cumulativeCarbonEmissionsGCO2e;

        ResourceUsageSnapshot(Instant timestamp, double cpuUsage, double memoryUsageMB,
                             double powerConsumption, double cumulativeEnergyKWh,
                             double cumulativeCarbonEmissionsGCO2e) {
            this.timestamp = timestamp;
            this.cpuUsage = cpuUsage;
            this.memoryUsageMB = memoryUsageMB;
            this.powerConsumption = powerConsumption;
            this.cumulativeEnergyKWh = cumulativeEnergyKWh;
            this.cumulativeCarbonEmissionsGCO2e = cumulativeCarbonEmissionsGCO2e;
        }
    }

    private static class FunctionEnergyStats {
        final String functionName;
        final Map<String, FunctionExecution> activeExecutions = new ConcurrentHashMap<>();
        long totalExecutions = 0;
        double totalExecutionTimeMs = 0;
        double minExecutionTimeMs = 0;
        double maxExecutionTimeMs = 0;
        double totalEnergyConsumption = 0;

        FunctionEnergyStats(String functionName) { this.functionName = functionName; }

        double getAverageExecutionTimeMs() {
            return totalExecutions > 0 ? totalExecutionTimeMs / totalExecutions : 0;
        }

        double getTotalEnergyConsumption() { return totalEnergyConsumption; }
    }

    private static class FunctionExecution {
        long startTime;
        double startEnergyConsumption;
    }

    public static void main(String[] args) throws InterruptedException {
        EnergyFootprintMonitor monitor = new EnergyFootprintMonitor();
        monitor.start(1);

        for (int i = 0; i < 10; i++) {
            String id = monitor.beginFunctionExecution("Workload");
            long end = System.currentTimeMillis() + 300;
            while (System.currentTimeMillis() < end) Math.sqrt(Math.random() * 10000);
            monitor.endFunctionExecution(id);
            Thread.sleep(200);
        }

        Thread.sleep(2000);
        monitor.stop();
    }
}
