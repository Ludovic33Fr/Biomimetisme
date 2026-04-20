package strategie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class StrategieEvolutionnaire {

    private static final int MU = 10;
    private static final int LAMBDA = 70;
    private static final int NB_DIMENSIONS = 10;
    private static final int NB_GENERATIONS = 100;
    private static final double SIGMA_INITIAL = 1.0;
    private static final double TAU = 1.0 / Math.sqrt(2 * NB_DIMENSIONS);
    private static final double TAU_PRIME = 1.0 / Math.sqrt(2 * Math.sqrt(NB_DIMENSIONS));

    private Random random = new Random();

    private class Individu {
        private double[] parametres;
        private double[] sigmas;
        private double fitness;

        public Individu() {
            parametres = new double[NB_DIMENSIONS];
            sigmas = new double[NB_DIMENSIONS];

            for (int i = 0; i < NB_DIMENSIONS; i++) {
                parametres[i] = random.nextDouble() * 10 - 5;
                sigmas[i] = SIGMA_INITIAL;
            }

            calculerFitness();
        }

        public Individu(double[] parametres, double[] sigmas) {
            this.parametres = parametres;
            this.sigmas = sigmas;
            calculerFitness();
        }

        private void calculerFitness() {
            double somme = 0;
            for (int i = 0; i < NB_DIMENSIONS - 1; i++) {
                double term1 = 100 * Math.pow(parametres[i + 1] - Math.pow(parametres[i], 2), 2);
                double term2 = Math.pow(1 - parametres[i], 2);
                somme += term1 + term2;
            }
            fitness = 1.0 / (somme + 1e-10);
        }

        public Individu muter() {
            double[] nouveauxParametres = new double[NB_DIMENSIONS];
            double[] nouveauxSigmas = new double[NB_DIMENSIONS];

            double facteurGlobal = Math.exp(TAU_PRIME * random.nextGaussian());

            for (int i = 0; i < NB_DIMENSIONS; i++) {
                nouveauxSigmas[i] = sigmas[i] * facteurGlobal * Math.exp(TAU * random.nextGaussian());

                if (nouveauxSigmas[i] < 1e-5) {
                    nouveauxSigmas[i] = 1e-5;
                }

                nouveauxParametres[i] = parametres[i] + nouveauxSigmas[i] * random.nextGaussian();
            }

            return new Individu(nouveauxParametres, nouveauxSigmas);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("f(");
            for (int i = 0; i < Math.min(3, NB_DIMENSIONS); i++) {
                sb.append(String.format("%.3f", parametres[i]));
                if (i < Math.min(3, NB_DIMENSIONS) - 1) sb.append(", ");
            }
            if (NB_DIMENSIONS > 3) sb.append(", ...");
            sb.append(") = ").append(String.format("%.6f", 1.0 / fitness - 1e-10));
            sb.append(", σ = ").append(String.format("%.6f", sigmas[0]));
            return sb.toString();
        }
    }

    public void executer() {
        List<Individu> parents = new ArrayList<>();
        for (int i = 0; i < MU; i++) {
            parents.add(new Individu());
        }

        Collections.sort(parents, (a, b) -> Double.compare(b.fitness, a.fitness));

        System.out.println("Génération 0: " + parents.get(0));

        for (int generation = 1; generation <= NB_GENERATIONS; generation++) {
            List<Individu> enfants = new ArrayList<>();

            for (int i = 0; i < LAMBDA; i++) {
                Individu parent = parents.get(random.nextInt(MU));
                Individu enfant = parent.muter();
                enfants.add(enfant);
            }

            Collections.sort(enfants, (a, b) -> Double.compare(b.fitness, a.fitness));
            parents = new ArrayList<>(enfants.subList(0, MU));

            if (generation % 10 == 0 || generation == NB_GENERATIONS) {
                System.out.println("Génération " + generation + ": " + parents.get(0));
            }
        }
    }

    public static void main(String[] args) {
        StrategieEvolutionnaire se = new StrategieEvolutionnaire();
        se.executer();
    }
}
