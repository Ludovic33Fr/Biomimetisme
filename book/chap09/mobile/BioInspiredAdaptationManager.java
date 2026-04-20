package mobile;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire d'adaptation bio-inspiré pour une application mobile.
 * Adapte dynamiquement consommation GPS/réseau/rendu selon batterie, signal, précision.
 * Inspiré des mécanismes métaboliques des organismes vivants.
 */
public class BioInspiredAdaptationManager {

    public enum SystemState { CRITICAL, RESTRICTED, NORMAL, ABUNDANT }

    public static class AdaptationParameters {
        public int gpsUpdateInterval;
        public int gpsAccuracy;
        public int mapDataUpdateInterval;
        public int renderingDetail;
        public double dataPrefetchRadius;
        public boolean advancedFeaturesEnabled;

        public AdaptationParameters() {
            this.gpsUpdateInterval = 5;
            this.gpsAccuracy = 10;
            this.mapDataUpdateInterval = 60;
            this.renderingDetail = 2;
            this.dataPrefetchRadius = 5.0;
            this.advancedFeaturesEnabled = true;
        }

        @Override
        public String toString() {
            return String.format(
                "GPS: %ds/%dm, Map: %ds, Detail: %d, Prefetch: %.1fkm, Advanced: %b",
                gpsUpdateInterval, gpsAccuracy, mapDataUpdateInterval,
                renderingDetail, dataPrefetchRadius, advancedFeaturesEnabled
            );
        }
    }

    private SystemState currentState;
    private AdaptationParameters currentParameters;
    private final Map<SystemState, AdaptationParameters> stateParameters;
    private final ReinforcementLearner learner;

    public BatteryMonitor batteryMonitor;
    private final ConnectivityMonitor connectivityMonitor;
    private final LocationQualityMonitor locationMonitor;
    private final PerformanceMonitor performanceMonitor;

    public BioInspiredAdaptationManager() {
        this.currentState = SystemState.NORMAL;
        this.currentParameters = new AdaptationParameters();
        this.stateParameters = new EnumMap<>(SystemState.class);

        initializeStateParameters();

        this.learner = new ReinforcementLearner();

        this.batteryMonitor = new BatteryMonitor();
        this.connectivityMonitor = new ConnectivityMonitor();
        this.locationMonitor = new LocationQualityMonitor();
        this.performanceMonitor = new PerformanceMonitor();
    }

    private void initializeStateParameters() {
        AdaptationParameters critical = new AdaptationParameters();
        critical.gpsUpdateInterval = 15;
        critical.gpsAccuracy = 50;
        critical.mapDataUpdateInterval = 300;
        critical.renderingDetail = 0;
        critical.dataPrefetchRadius = 1.0;
        critical.advancedFeaturesEnabled = false;
        stateParameters.put(SystemState.CRITICAL, critical);

        AdaptationParameters restricted = new AdaptationParameters();
        restricted.gpsUpdateInterval = 10;
        restricted.gpsAccuracy = 20;
        restricted.mapDataUpdateInterval = 120;
        restricted.renderingDetail = 1;
        restricted.dataPrefetchRadius = 2.0;
        restricted.advancedFeaturesEnabled = false;
        stateParameters.put(SystemState.RESTRICTED, restricted);

        stateParameters.put(SystemState.NORMAL, new AdaptationParameters());

        AdaptationParameters abundant = new AdaptationParameters();
        abundant.gpsUpdateInterval = 1;
        abundant.gpsAccuracy = 5;
        abundant.mapDataUpdateInterval = 30;
        abundant.renderingDetail = 3;
        abundant.dataPrefetchRadius = 10.0;
        abundant.advancedFeaturesEnabled = true;
        stateParameters.put(SystemState.ABUNDANT, abundant);
    }

    public void adaptToCurrentConditions() {
        double batteryLevel = batteryMonitor.getBatteryLevel();
        boolean isCharging = batteryMonitor.isCharging();
        int signalStrength = connectivityMonitor.getSignalStrength();
        int locationAccuracy = locationMonitor.getCurrentAccuracy();

        SystemState newState = determineSystemState(batteryLevel, isCharging, signalStrength, locationAccuracy);

        if (newState != currentState) {
            currentState = newState;
            AdaptationParameters baseParams = stateParameters.get(currentState);
            currentParameters = learner.optimizeParameters(baseParams, currentState);

            System.out.println("État: " + currentState);
            System.out.println("Paramètres: " + currentParameters);

            applyParameters(currentParameters);
        } else {
            finetuneParameters(batteryLevel, signalStrength, locationAccuracy);
        }
    }

    private SystemState determineSystemState(double batteryLevel, boolean isCharging,
                                           int signalStrength, int locationAccuracy) {
        if (isCharging && batteryLevel > 50) return SystemState.ABUNDANT;
        if (batteryLevel < 15 || (signalStrength < 2 && locationAccuracy > 50)) return SystemState.CRITICAL;
        if (batteryLevel < 30 || signalStrength < 3) return SystemState.RESTRICTED;
        return SystemState.NORMAL;
    }

    private void applyParameters(AdaptationParameters params) {
        System.out.println("Application: " + params);
    }

    private void finetuneParameters(double batteryLevel, int signalStrength, int locationAccuracy) {
        if (locationAccuracy > 30 && currentParameters.gpsUpdateInterval < 10) {
            currentParameters.gpsUpdateInterval += 1;
        } else if (locationAccuracy < 10 && currentParameters.gpsUpdateInterval > 2) {
            currentParameters.gpsUpdateInterval -= 1;
        }

        if (signalStrength < 2 && currentParameters.dataPrefetchRadius < 8.0) {
            currentParameters.dataPrefetchRadius += 1.0;
        }

        applyParameters(currentParameters);
        performanceMonitor.recordAdaptation(currentParameters);
    }

    private class ReinforcementLearner {
        public AdaptationParameters optimizeParameters(AdaptationParameters baseParams, SystemState state) {
            AdaptationParameters opt = new AdaptationParameters();
            opt.gpsUpdateInterval = baseParams.gpsUpdateInterval;
            opt.gpsAccuracy = baseParams.gpsAccuracy;
            opt.mapDataUpdateInterval = baseParams.mapDataUpdateInterval;
            opt.renderingDetail = baseParams.renderingDetail;
            opt.dataPrefetchRadius = baseParams.dataPrefetchRadius;
            opt.advancedFeaturesEnabled = baseParams.advancedFeaturesEnabled;

            Map<String, Double> history = performanceMonitor.getPerformanceHistory();

            if (history.containsKey("batteryDrain") && history.get("batteryDrain") > 0.2) {
                opt.gpsUpdateInterval += 1;
                opt.renderingDetail = Math.max(0, opt.renderingDetail - 1);
            }

            if (history.containsKey("responseTime") && history.get("responseTime") > 500
                && state != SystemState.CRITICAL) {
                opt.dataPrefetchRadius += 0.5;
            }

            return opt;
        }
    }

    public static class BatteryMonitor {
        public double getBatteryLevel() { return 45.0; }
        public boolean isCharging() { return false; }
    }

    public static class ConnectivityMonitor {
        public int getSignalStrength() { return 3; }
    }

    public static class LocationQualityMonitor {
        public int getCurrentAccuracy() { return 15; }
    }

    public static class PerformanceMonitor {
        private final Map<String, Double> performanceMetrics = new HashMap<>();

        public void recordAdaptation(AdaptationParameters params) {}

        public Map<String, Double> getPerformanceHistory() {
            performanceMetrics.put("batteryDrain", 0.15);
            performanceMetrics.put("responseTime", 350.0);
            performanceMetrics.put("userSatisfaction", 4.2);
            return performanceMetrics;
        }
    }

    public static void main(String[] args) {
        BioInspiredAdaptationManager manager = new BioInspiredAdaptationManager();

        System.out.println("=== Adaptation initiale ===");
        manager.adaptToCurrentConditions();

        System.out.println("\n=== Batterie faible ===");
        manager.batteryMonitor = new BatteryMonitor() {
            @Override public double getBatteryLevel() { return 14.0; }
            @Override public boolean isCharging() { return false; }
        };
        manager.adaptToCurrentConditions();

        System.out.println("\n=== Chargeur connecté ===");
        manager.batteryMonitor = new BatteryMonitor() {
            @Override public double getBatteryLevel() { return 60.0; }
            @Override public boolean isCharging() { return true; }
        };
        manager.adaptToCurrentConditions();
    }
}
