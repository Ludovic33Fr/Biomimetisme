package reseau;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReseauTrophique {

    private static class Espece {
        private String nom;
        private List<Espece> proies;
        private List<Espece> predateurs;
        private double biomasse;

        public Espece(String nom, double biomasseInitiale) {
            this.nom = nom;
            this.proies = new ArrayList<>();
            this.predateurs = new ArrayList<>();
            this.biomasse = biomasseInitiale;
        }

        public void ajouterProie(Espece proie) {
            proies.add(proie);
            proie.ajouterPredateur(this);
        }

        public void ajouterPredateur(Espece predateur) {
            predateurs.add(predateur);
        }

        @Override
        public String toString() {
            return nom + " (Biomasse: " + String.format("%.2f", biomasse) + ")";
        }
    }

    private Map<String, Espece> especes = new HashMap<>();

    public void ajouterEspece(String nom, double biomasseInitiale) {
        especes.put(nom, new Espece(nom, biomasseInitiale));
    }

    public void ajouterRelation(String nomPredateur, String nomProie) {
        Espece predateur = especes.get(nomPredateur);
        Espece proie = especes.get(nomProie);

        if (predateur != null && proie != null) {
            predateur.ajouterProie(proie);
        } else {
            System.err.println("Erreur: Espèce non trouvée pour la relation " +
                               nomPredateur + " -> " + nomProie);
        }
    }

    public void simulerPasTemps() {
        Map<String, Double> changementsBiomasse = new HashMap<>();

        for (Espece espece : especes.values()) {
            double croissance = 0.0;
            double perteParPredation = 0.0;
            double gainParPredation = 0.0;

            if (espece.proies.isEmpty() && espece.predateurs.size() > 0) {
                croissance = espece.biomasse * 0.1;
            }

            for (Espece predateur : espece.predateurs) {
                perteParPredation += 0.01 * espece.biomasse * predateur.biomasse;
            }

            for (Espece proie : espece.proies) {
                gainParPredation += 0.05 * espece.biomasse * proie.biomasse;
            }

            double changementNet = croissance + gainParPredation - perteParPredation;
            changementsBiomasse.put(espece.nom, changementNet);
        }

        for (Map.Entry<String, Double> entry : changementsBiomasse.entrySet()) {
            Espece espece = especes.get(entry.getKey());
            espece.biomasse += entry.getValue();
            if (espece.biomasse < 0) espece.biomasse = 0;
        }
    }

    public void afficherEtat() {
        System.out.println("État du réseau trophique:");
        for (Espece espece : especes.values()) {
            System.out.println("- " + espece);
            if (!espece.proies.isEmpty()) {
                System.out.print("  Mange: ");
                for (Espece proie : espece.proies) System.out.print(proie.nom + " ");
                System.out.println();
            }
            if (!espece.predateurs.isEmpty()) {
                System.out.print("  Mangé par: ");
                for (Espece predateur : espece.predateurs) System.out.print(predateur.nom + " ");
                System.out.println();
            }
        }
        System.out.println("--------------------");
    }

    public static void main(String[] args) {
        ReseauTrophique reseau = new ReseauTrophique();

        reseau.ajouterEspece("Plantes", 1000.0);
        reseau.ajouterEspece("Sauterelles", 200.0);
        reseau.ajouterEspece("Souris", 150.0);
        reseau.ajouterEspece("Grenouilles", 50.0);
        reseau.ajouterEspece("Serpents", 20.0);
        reseau.ajouterEspece("Faucons", 10.0);

        reseau.ajouterRelation("Sauterelles", "Plantes");
        reseau.ajouterRelation("Souris", "Plantes");
        reseau.ajouterRelation("Grenouilles", "Sauterelles");
        reseau.ajouterRelation("Serpents", "Souris");
        reseau.ajouterRelation("Serpents", "Grenouilles");
        reseau.ajouterRelation("Faucons", "Souris");
        reseau.ajouterRelation("Faucons", "Serpents");

        reseau.afficherEtat();

        for (int i = 0; i < 10; i++) {
            System.out.println("Simulation - Pas de temps " + (i + 1));
            reseau.simulerPasTemps();
            reseau.afficherEtat();
        }
    }
}
