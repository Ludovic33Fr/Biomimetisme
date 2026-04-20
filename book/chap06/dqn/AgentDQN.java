package dqn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class AgentDQN {
    private ReseauNeuronal qReseau;
    private ReseauNeuronal qReseauCible;
    private double gamma;
    private double epsilon;
    private double epsilonMin;
    private double epsilonDecay;
    private int tailleMemoire;
    private List<Experience> memoire;
    private int tailleLot;
    private Random random;

    private static class Experience {
        double[] etat;
        int action;
        double recompense;
        double[] etatSuivant;
        boolean terminal;

        Experience(double[] etat, int action, double recompense, double[] etatSuivant, boolean terminal) {
            this.etat = etat;
            this.action = action;
            this.recompense = recompense;
            this.etatSuivant = etatSuivant;
            this.terminal = terminal;
        }
    }

    public AgentDQN(int tailleEtat, int nbActions, double gamma, double epsilon,
                   double epsilonMin, double epsilonDecay, int tailleMemoire, int tailleLot) {
        this.gamma = gamma;
        this.epsilon = epsilon;
        this.epsilonMin = epsilonMin;
        this.epsilonDecay = epsilonDecay;
        this.tailleMemoire = tailleMemoire;
        this.tailleLot = tailleLot;
        this.memoire = new ArrayList<>();
        this.random = new Random();

        int[] tailleCouches = {tailleEtat, 24, 24, nbActions};
        this.qReseau = new ReseauNeuronal(tailleCouches);
        this.qReseauCible = new ReseauNeuronal(tailleCouches);

        mettreAJourReseauCible();
    }

    public int choisirAction(double[] etat) {
        if (random.nextDouble() < epsilon) {
            return random.nextInt(qReseau.getNbSorties());
        }

        double[] qValeurs = qReseau.propagationAvant(etat);
        int meilleurAction = 0;
        for (int i = 1; i < qValeurs.length; i++) {
            if (qValeurs[i] > qValeurs[meilleurAction]) meilleurAction = i;
        }

        return meilleurAction;
    }

    public void memoriser(double[] etat, int action, double recompense,
                         double[] etatSuivant, boolean terminal) {
        Experience experience = new Experience(etat, action, recompense, etatSuivant, terminal);

        if (memoire.size() >= tailleMemoire) memoire.remove(0);
        memoire.add(experience);
    }

    public void entrainer() {
        if (memoire.size() < tailleLot) return;

        List<Experience> lot = echantillonnerLot();

        for (Experience exp : lot) {
            double cible;
            if (exp.terminal) {
                cible = exp.recompense;
            } else {
                double[] qValeursSuivantes = qReseauCible.propagationAvant(exp.etatSuivant);
                double maxQ = qValeursSuivantes[0];
                for (int i = 1; i < qValeursSuivantes.length; i++) {
                    if (qValeursSuivantes[i] > maxQ) maxQ = qValeursSuivantes[i];
                }
                cible = exp.recompense + gamma * maxQ;
            }

            double[] qValeurs = qReseau.propagationAvant(exp.etat);
            double[] cibles = Arrays.copyOf(qValeurs, qValeurs.length);
            cibles[exp.action] = cible;

            qReseau.retropropagation(exp.etat, cibles);
        }

        if (epsilon > epsilonMin) {
            epsilon *= epsilonDecay;
        }
    }

    private List<Experience> echantillonnerLot() {
        List<Experience> lot = new ArrayList<>();
        Set<Integer> indices = new HashSet<>();

        while (indices.size() < tailleLot) {
            indices.add(random.nextInt(memoire.size()));
        }

        for (int indice : indices) lot.add(memoire.get(indice));

        return lot;
    }

    public void mettreAJourReseauCible() {
        qReseauCible.copierPoidsDe(qReseau);
    }

    /**
     * Réseau de neurones simplifié pour DQN. Dans une version réelle, il faudrait
     * implémenter propagation et rétropropagation complètes.
     */
    private static class ReseauNeuronal {
        private int[] tailleCouches;

        ReseauNeuronal(int[] tailleCouches) {
            this.tailleCouches = tailleCouches;
        }

        double[] propagationAvant(double[] entree) {
            return new double[tailleCouches[tailleCouches.length - 1]];
        }

        void retropropagation(double[] entree, double[] cible) {
            // À implémenter dans une version complète
        }

        void copierPoidsDe(ReseauNeuronal autre) {
            // À implémenter dans une version complète
        }

        int getNbSorties() {
            return tailleCouches[tailleCouches.length - 1];
        }
    }

    public static void main(String[] args) {
        AgentDQN agent = new AgentDQN(4, 2, 0.95, 1.0, 0.01, 0.995, 2000, 32);
        double[] etat = {0.0, 0.0, 0.0, 0.0};
        int action = agent.choisirAction(etat);
        System.out.println("Action choisie : " + action);
    }
}
