package modularite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReseauMetabolique {

    interface ComposantMetabolique {
        void traiter(Map<String, Double> metabolites);
        String getNom();
    }

    static class Enzyme implements ComposantMetabolique {
        private String nom;
        private String substrat;
        private String produit;
        private double tauxReaction;

        public Enzyme(String nom, String substrat, String produit, double tauxReaction) {
            this.nom = nom;
            this.substrat = substrat;
            this.produit = produit;
            this.tauxReaction = tauxReaction;
        }

        @Override
        public void traiter(Map<String, Double> metabolites) {
            if (metabolites.containsKey(substrat) && metabolites.get(substrat) > 0) {
                double quantiteSubstrat = metabolites.get(substrat);
                double quantiteTransformee = Math.min(quantiteSubstrat, tauxReaction);

                metabolites.put(substrat, quantiteSubstrat - quantiteTransformee);

                double quantiteProduitActuelle = metabolites.getOrDefault(produit, 0.0);
                metabolites.put(produit, quantiteProduitActuelle + quantiteTransformee);

                System.out.printf("Enzyme %s: %.2f %s -> %.2f %s\n",
                                 nom, quantiteTransformee, substrat, quantiteTransformee, produit);
            }
        }

        @Override
        public String getNom() {
            return nom;
        }
    }

    static class VoieMetabolique implements ComposantMetabolique {
        private String nom;
        private List<ComposantMetabolique> composants;

        public VoieMetabolique(String nom) {
            this.nom = nom;
            this.composants = new ArrayList<>();
        }

        public void ajouterComposant(ComposantMetabolique composant) {
            composants.add(composant);
        }

        @Override
        public void traiter(Map<String, Double> metabolites) {
            System.out.println("Début de la voie métabolique: " + nom);
            for (ComposantMetabolique composant : composants) {
                composant.traiter(metabolites);
            }
            System.out.println("Fin de la voie métabolique: " + nom);
        }

        @Override
        public String getNom() {
            return nom;
        }
    }

    static class Metabolisme {
        private String nom;
        private List<ComposantMetabolique> voies;
        private Map<String, Double> metabolites;

        public Metabolisme(String nom) {
            this.nom = nom;
            this.voies = new ArrayList<>();
            this.metabolites = new HashMap<>();
        }

        public void ajouterVoie(ComposantMetabolique voie) {
            voies.add(voie);
        }

        public void ajouterMetabolite(String nom, double quantite) {
            metabolites.put(nom, quantite);
        }

        public void simuler(int cycles) {
            System.out.println("Simulation du métabolisme: " + nom);
            System.out.println("État initial des métabolites: " + metabolites);

            for (int i = 0; i < cycles; i++) {
                System.out.println("\nCycle " + (i + 1) + ":");
                for (ComposantMetabolique voie : voies) {
                    voie.traiter(metabolites);
                }
                System.out.println("État des métabolites après le cycle " + (i + 1) + ": " + metabolites);
            }
        }
    }

    public static void main(String[] args) {
        Enzyme hexokinase = new Enzyme("Hexokinase", "Glucose", "Glucose-6-P", 0.5);
        Enzyme phosphoglucoisomerase = new Enzyme("Phosphoglucoisomerase", "Glucose-6-P", "Fructose-6-P", 0.4);
        Enzyme phosphofructokinase = new Enzyme("Phosphofructokinase", "Fructose-6-P", "Fructose-1,6-BP", 0.3);

        Enzyme pyruvateDehydrogenase = new Enzyme("Pyruvate Dehydrogenase", "Pyruvate", "Acetyl-CoA", 0.3);
        Enzyme citrateeSynthase = new Enzyme("Citrate Synthase", "Acetyl-CoA", "Citrate", 0.25);

        VoieMetabolique glycolyse = new VoieMetabolique("Glycolyse");
        glycolyse.ajouterComposant(hexokinase);
        glycolyse.ajouterComposant(phosphoglucoisomerase);
        glycolyse.ajouterComposant(phosphofructokinase);

        VoieMetabolique cycleTCA = new VoieMetabolique("Cycle de Krebs");
        cycleTCA.ajouterComposant(pyruvateDehydrogenase);
        cycleTCA.ajouterComposant(citrateeSynthase);

        Metabolisme metabolismeCellulaire = new Metabolisme("Métabolisme cellulaire");
        metabolismeCellulaire.ajouterVoie(glycolyse);
        metabolismeCellulaire.ajouterVoie(cycleTCA);

        metabolismeCellulaire.ajouterMetabolite("Glucose", 10.0);
        metabolismeCellulaire.ajouterMetabolite("Pyruvate", 5.0);

        metabolismeCellulaire.simuler(5);
    }
}
