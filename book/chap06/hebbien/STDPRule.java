package hebbien;

/**
 * Implémentation de la règle STDP (Spike-Timing-Dependent Plasticity).
 * La modification synaptique dépend de la différence temporelle entre les
 * activations pré et postsynaptiques :
 *   - Δt > 0 (pré avant post) : potentialisation (LTP)
 *   - Δt < 0 (post avant pré) : dépression (LTD)
 */
public class STDPRule {

    private final double aPlus;
    private final double aMoins;
    private final double tauPlus;
    private final double tauMoins;

    public STDPRule() {
        this(0.1, 0.12, 20, 20);
    }

    public STDPRule(double aPlus, double aMoins, double tauPlus, double tauMoins) {
        this.aPlus = aPlus;
        this.aMoins = aMoins;
        this.tauPlus = tauPlus;
        this.tauMoins = tauMoins;
    }

    /**
     * Calcule la modification synaptique pour une différence temporelle donnée (en ms).
     */
    public double calculerModification(double deltaT) {
        if (deltaT > 0) {
            return aPlus * Math.exp(-deltaT / tauPlus);
        } else {
            return -aMoins * Math.exp(deltaT / tauMoins);
        }
    }

    public static void main(String[] args) {
        STDPRule stdp = new STDPRule();
        System.out.println("Courbe STDP :");
        for (double dt = -50; dt <= 50; dt += 5) {
            System.out.printf("  Δt = %+5.0f ms → Δw = %+.4f%n", dt, stdp.calculerModification(dt));
        }
    }
}
