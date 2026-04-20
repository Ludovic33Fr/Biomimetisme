package adaptive;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service adaptatif bio-inspiré qui ajuste sa qualité (et sa consommation)
 * en fonction des conditions système (charge, batterie).
 * Inspiré de l'homéostasie biologique.
 */
public class AdaptiveBioInspiredService {

    public enum QualityLevel { LOW, MEDIUM, HIGH }

    private QualityLevel currentQuality;
    private final ScheduledExecutorService monitorScheduler;
    private final Random random = new Random();

    private double systemLoad = 0.5;
    private double batteryLevel = 80.0;

    public AdaptiveBioInspiredService() {
        this.currentQuality = QualityLevel.MEDIUM;
        this.monitorScheduler = Executors.newScheduledThreadPool(1);
        startHomeostaticControl();
    }

    private void startHomeostaticControl() {
        monitorScheduler.scheduleAtFixedRate(() -> {
            simulateSystemChanges();
            adaptQualityLevel();
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void simulateSystemChanges() {
        systemLoad += (random.nextDouble() - 0.5) * 0.2;
        systemLoad = Math.max(0.1, Math.min(0.9, systemLoad));

        if (random.nextDouble() > 0.8) {
            // Sur secteur
        } else {
            batteryLevel -= random.nextDouble() * 2.0;
            batteryLevel = Math.max(0.0, batteryLevel);
        }
        System.out.printf("État: Charge=%.2f, Batterie=%.1f%%%n", systemLoad, batteryLevel);
    }

    private void adaptQualityLevel() {
        QualityLevel previousQuality = currentQuality;

        if (batteryLevel < 20.0 || systemLoad > 0.8) {
            currentQuality = QualityLevel.LOW;
        } else if (batteryLevel > 80.0 && systemLoad < 0.3) {
            currentQuality = QualityLevel.HIGH;
        } else {
            currentQuality = QualityLevel.MEDIUM;
        }

        if (currentQuality != previousQuality) {
            System.out.println("Qualité : " + currentQuality);
            applyQualityConfiguration();
        }
    }

    private void applyQualityConfiguration() {
        switch (currentQuality) {
            case LOW:
                System.out.println("  → Résolution réduite, traitements simplifiés");
                break;
            case MEDIUM:
                System.out.println("  → Qualité standard");
                break;
            case HIGH:
                System.out.println("  → Qualité maximale, fonctions avancées activées");
                break;
        }
    }

    public void performOperation() {
        double energyConsumed;
        switch (currentQuality) {
            case LOW: energyConsumed = 0.1; break;
            case HIGH: energyConsumed = 1.0; break;
            default: energyConsumed = 0.5;
        }
        System.out.printf("Opération (qualité %s): %.2f unités%n", currentQuality, energyConsumed);
    }

    public void shutdown() {
        monitorScheduler.shutdown();
        try {
            monitorScheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AdaptiveBioInspiredService service = new AdaptiveBioInspiredService();

        ScheduledExecutorService usage = Executors.newScheduledThreadPool(1);
        usage.scheduleAtFixedRate(service::performOperation, 1, 3, TimeUnit.SECONDS);

        Thread.sleep(30000);
        usage.shutdown();
        service.shutdown();
    }
}
