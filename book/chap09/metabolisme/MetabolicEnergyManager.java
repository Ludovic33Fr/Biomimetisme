package metabolisme;

/**
 * Gestionnaire d'énergie bio-inspiré qui adapte la consommation
 * en fonction de la charge et des ressources disponibles.
 * Inspiré du métabolisme régulé des organismes vivants.
 */
public class MetabolicEnergyManager {

    public enum EnergyState {
        HIBERNATION,
        LOW_POWER,
        STANDARD,
        HIGH_PERFORMANCE
    }

    private EnergyState currentState;
    private double batteryLevel;
    private double systemLoad;
    private boolean isPlugged;

    public MetabolicEnergyManager() {
        this.currentState = EnergyState.STANDARD;
        this.batteryLevel = 100.0;
        this.systemLoad = 0.0;
        this.isPlugged = true;
    }

    public void updateEnergyState(double batteryLevel, double systemLoad, boolean isPlugged) {
        this.batteryLevel = batteryLevel;
        this.systemLoad = systemLoad;
        this.isPlugged = isPlugged;

        if (isPlugged) {
            currentState = (systemLoad > 0.8) ? EnergyState.HIGH_PERFORMANCE : EnergyState.STANDARD;
        } else {
            if (batteryLevel < 20.0) currentState = EnergyState.HIBERNATION;
            else if (batteryLevel < 50.0) currentState = EnergyState.LOW_POWER;
            else currentState = EnergyState.STANDARD;
        }

        applyEnergyPolicies();
    }

    private void applyEnergyPolicies() {
        switch (currentState) {
            case HIBERNATION:
                disableNonEssentialServices();
                minimizeCPUFrequency();
                maximizeScreenTimeout();
                break;
            case LOW_POWER:
                optimizeBackgroundProcesses();
                reduceCPUFrequency();
                dimScreen();
                break;
            case STANDARD:
                balancePerformanceAndEnergy();
                break;
            case HIGH_PERFORMANCE:
                enableAllServices();
                maximizeCPUFrequency();
                break;
        }
    }

    private void disableNonEssentialServices() { System.out.println("Désactivation services non essentiels"); }
    private void minimizeCPUFrequency() { System.out.println("CPU au minimum"); }
    private void maximizeScreenTimeout() { System.out.println("Écran : timeout max"); }
    private void optimizeBackgroundProcesses() { System.out.println("Optimisation processus background"); }
    private void reduceCPUFrequency() { System.out.println("Réduction CPU"); }
    private void dimScreen() { System.out.println("Écran : luminosité réduite"); }
    private void balancePerformanceAndEnergy() { System.out.println("Équilibre perf/énergie"); }
    private void enableAllServices() { System.out.println("Tous services activés"); }
    private void maximizeCPUFrequency() { System.out.println("CPU au maximum"); }

    public EnergyState getCurrentState() { return currentState; }

    public static void main(String[] args) {
        MetabolicEnergyManager manager = new MetabolicEnergyManager();

        System.out.println("=== Scénario 1: Batterie faible ===");
        manager.updateEnergyState(15.0, 0.3, false);
        System.out.println("État: " + manager.getCurrentState());

        System.out.println("\n=== Scénario 2: Charge élevée sur secteur ===");
        manager.updateEnergyState(90.0, 0.9, true);
        System.out.println("État: " + manager.getCurrentState());

        System.out.println("\n=== Scénario 3: Batterie moyenne sans alim ===");
        manager.updateEnergyState(45.0, 0.5, false);
        System.out.println("État: " + manager.getCurrentState());
    }
}
