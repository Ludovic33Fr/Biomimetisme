package regulateur;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class RegulateurAdaptatif {

    private double kp, ki, kd;

    private double temperatureActuelle;
    private double temperatureCible;
    private double erreurPrecedente;
    private double sommeErreurs;

    private double tauxApprentissage;
    private List<Double> erreursMoyennes;
    private int fenetreAdaptation;

    private Random random;

    public RegulateurAdaptatif(double temperatureCible, double kpInitial,
                              double kiInitial, double kdInitial,
                              double tauxApprentissage, int fenetreAdaptation) {
        this.temperatureCible = temperatureCible;
        this.temperatureActuelle = temperatureCible;
        this.kp = kpInitial;
        this.ki = kiInitial;
        this.kd = kdInitial;
        this.erreurPrecedente = 0;
        this.sommeErreurs = 0;
        this.tauxApprentissage = tauxApprentissage;
        this.fenetreAdaptation = fenetreAdaptation;
        this.erreursMoyennes = new ArrayList<>();
        this.random = new Random();
    }

    public double calculerCommande() {
        double erreur = temperatureCible - temperatureActuelle;

        double p = kp * erreur;

        sommeErreurs += erreur;
        double i = ki * sommeErreurs;

        double d = kd * (erreur - erreurPrecedente);
        erreurPrecedente = erreur;

        return p + i + d;
    }

    public void actualiserTemperature(double commande) {
        temperatureActuelle += commande * 0.1;
        temperatureActuelle += (random.nextDouble() - 0.5) * 2.0;
    }

    public void adapter() {
        if (erreursMoyennes.isEmpty()) return;
        double erreurMoyenne = 0;
        for (Double erreur : erreursMoyennes) erreurMoyenne += Math.abs(erreur);
        erreurMoyenne /= erreursMoyennes.size();

        if (erreursMoyennes.size() >= 2 &&
            erreurMoyenne > erreursMoyennes.get(erreursMoyennes.size() - 2)) {

            kp += tauxApprentissage * erreurMoyenne;
            if (Math.abs(sommeErreurs) > 5.0) ki += tauxApprentissage * 0.1;
            if (Math.abs(erreurPrecedente - (temperatureCible - temperatureActuelle)) > 1.0) {
                kd += tauxApprentissage * 0.05;
            }

            System.out.println("Adaptation: kp=" + kp + ", ki=" + ki + ", kd=" + kd);
        }

        erreursMoyennes.add(erreurMoyenne);
        if (erreursMoyennes.size() > fenetreAdaptation) erreursMoyennes.remove(0);
    }

    public void simuler(int nombreIterations) {
        System.out.println("Début de la simulation du régulateur adaptatif");
        System.out.println("Température cible : " + temperatureCible);
        System.out.println("Paramètres initiaux : kp=" + kp + ", ki=" + ki + ", kd=" + kd);

        for (int i = 0; i < nombreIterations; i++) {
            double commande = calculerCommande();
            actualiserTemperature(commande);
            double erreur = temperatureCible - temperatureActuelle;

            if (i % fenetreAdaptation == 0 && i > 0) adapter();

            if (i % 10 == 0) {
                System.out.println("Itération " + i + " - T : " + temperatureActuelle + " - E : " + erreur);
            }
        }

        System.out.println("Fin de la simulation. kp=" + kp + ", ki=" + ki + ", kd=" + kd);
    }

    public static void main(String[] args) {
        RegulateurAdaptatif regulateur = new RegulateurAdaptatif(25.0, 0.5, 0.1, 0.01, 0.01, 20);
        regulateur.simuler(200);
    }
}
