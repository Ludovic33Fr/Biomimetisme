package darwin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class EvolutionDarwinienne {

    private static final int TAILLE_POPULATION = 100;
    private static final int LONGUEUR_GENOME = 20;
    private static final double TAUX_MUTATION = 0.01;
    private static final int NB_GENERATIONS = 200;
    private static final int TAILLE_TOURNOI = 5;

    private Random random = new Random();

    private class Individu {
        private boolean[] genome;
        private double fitness;

        public Individu() {
            genome = new boolean[LONGUEUR_GENOME];
            for (int i = 0; i < LONGUEUR_GENOME; i++) {
                genome[i] = random.nextBoolean();
            }
            calculerFitness();
        }

        public Individu(boolean[] genome) {
            this.genome = genome;
            calculerFitness();
        }

        private void calculerFitness() {
            int count = 0;
            for (boolean gene : genome) {
                if (gene) count++;
            }
            fitness = count;
        }

        public Individu croiser(Individu autre) {
            boolean[] genomeEnfant = new boolean[LONGUEUR_GENOME];
            int pointCroisement = random.nextInt(LONGUEUR_GENOME);
            for (int i = 0; i < LONGUEUR_GENOME; i++) {
                genomeEnfant[i] = (i < pointCroisement) ? this.genome[i] : autre.genome[i];
            }
            return new Individu(genomeEnfant);
        }

        public void muter() {
            for (int i = 0; i < LONGUEUR_GENOME; i++) {
                if (random.nextDouble() < TAUX_MUTATION) {
                    genome[i] = !genome[i];
                }
            }
            calculerFitness();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (boolean gene : genome) sb.append(gene ? "1" : "0");
            return sb.toString() + " (fitness: " + fitness + ")";
        }
    }

    public void executer() {
        List<Individu> population = new ArrayList<>();
        for (int i = 0; i < TAILLE_POPULATION; i++) {
            population.add(new Individu());
        }

        afficherStatistiques(population, 0);

        for (int generation = 1; generation <= NB_GENERATIONS; generation++) {
            List<Individu> nouvellePopulation = new ArrayList<>();
            while (nouvellePopulation.size() < TAILLE_POPULATION) {
                Individu parent1 = selectionTournoi(population);
                Individu parent2 = selectionTournoi(population);
                Individu enfant = parent1.croiser(parent2);
                enfant.muter();
                nouvellePopulation.add(enfant);
            }
            population = nouvellePopulation;

            if (generation % 10 == 0 || generation == NB_GENERATIONS) {
                afficherStatistiques(population, generation);
            }
        }

        Individu meilleur = trouverMeilleur(population);
        System.out.println("\nMeilleur individu final: " + meilleur);
    }

    private Individu selectionTournoi(List<Individu> population) {
        Individu meilleur = null;
        for (int i = 0; i < TAILLE_TOURNOI; i++) {
            Individu candidat = population.get(random.nextInt(population.size()));
            if (meilleur == null || candidat.fitness > meilleur.fitness) {
                meilleur = candidat;
            }
        }
        return meilleur;
    }

    private Individu trouverMeilleur(List<Individu> population) {
        Individu meilleur = population.get(0);
        for (Individu individu : population) {
            if (individu.fitness > meilleur.fitness) meilleur = individu;
        }
        return meilleur;
    }

    private void afficherStatistiques(List<Individu> population, int generation) {
        double sommeFitness = 0;
        double minFitness = Double.MAX_VALUE;
        double maxFitness = Double.MIN_VALUE;

        for (Individu individu : population) {
            sommeFitness += individu.fitness;
            minFitness = Math.min(minFitness, individu.fitness);
            maxFitness = Math.max(maxFitness, individu.fitness);
        }

        double moyenneFitness = sommeFitness / population.size();
        System.out.printf("Génération %3d: Min = %5.2f, Moyenne = %5.2f, Max = %5.2f\n",
                         generation, minFitness, moyenneFitness, maxFitness);
    }

    public static void main(String[] args) {
        EvolutionDarwinienne simulation = new EvolutionDarwinienne();
        simulation.executer();
    }
}
