package hierarchie;

import java.util.ArrayList;
import java.util.List;

interface NiveauOrganisation {
    void maintenir();
    void adapter();
    void interagir(NiveauOrganisation autre);
}

abstract class NiveauBiologique implements NiveauOrganisation {
    protected String nom;
    protected List<NiveauBiologique> composants;
    protected NiveauBiologique parent;

    public NiveauBiologique(String nom) {
        this.nom = nom;
        this.composants = new ArrayList<>();
    }

    public void ajouterComposant(NiveauBiologique composant) {
        composants.add(composant);
        composant.setParent(this);
    }

    public void setParent(NiveauBiologique parent) {
        this.parent = parent;
    }

    @Override
    public void maintenir() {
        System.out.println(nom + " maintient son homéostasie");
        for (NiveauBiologique composant : composants) {
            composant.maintenir();
        }
    }

    @Override
    public void adapter() {
        System.out.println(nom + " s'adapte aux changements environnementaux");
    }

    @Override
    public void interagir(NiveauOrganisation autre) {
        System.out.println(nom + " interagit avec " + ((NiveauBiologique)autre).nom);
    }

    public abstract void comportementEmergent();
}

class Cellule extends NiveauBiologique {
    private String type;

    public Cellule(String nom, String type) {
        super(nom);
        this.type = type;
    }

    @Override
    public void comportementEmergent() {
        System.out.println(nom + " (cellule " + type + ") exprime des propriétés émergentes : " +
                          "métabolisme, réponse aux stimuli, reproduction");
    }

    public void diviser() {
        System.out.println(nom + " se divise");
    }

    public void metaboliser() {
        System.out.println(nom + " métabolise des nutriments");
    }
}

class Tissu extends NiveauBiologique {
    private String fonction;

    public Tissu(String nom, String fonction) {
        super(nom);
        this.fonction = fonction;
    }

    @Override
    public void comportementEmergent() {
        System.out.println(nom + " (tissu " + fonction + ") exprime des propriétés émergentes : " +
                          "organisation spatiale, communication intercellulaire, fonction spécialisée");
    }

    public void remplirFonction() {
        System.out.println(nom + " remplit sa fonction : " + fonction);
    }
}

class Organe extends NiveauBiologique {
    private String systeme;

    public Organe(String nom, String systeme) {
        super(nom);
        this.systeme = systeme;
    }

    @Override
    public void comportementEmergent() {
        System.out.println(nom + " (organe du système " + systeme + ") exprime des propriétés émergentes : " +
                          "fonction physiologique complexe, régulation autonome");
    }

    public void fonctionnerDansSysteme() {
        System.out.println(nom + " fonctionne dans le système " + systeme);
    }
}

class Organisme extends NiveauBiologique {
    private String espece;

    public Organisme(String nom, String espece) {
        super(nom);
        this.espece = espece;
    }

    @Override
    public void comportementEmergent() {
        System.out.println(nom + " (organisme de l'espèce " + espece + ") exprime des propriétés émergentes : " +
                          "conscience, comportement complexe, reproduction sexuée");
    }

    public void seReproduire() {
        System.out.println(nom + " se reproduit");
    }
}

class Population extends NiveauBiologique {
    private int taille;

    public Population(String nom, int taille) {
        super(nom);
        this.taille = taille;
    }

    @Override
    public void comportementEmergent() {
        System.out.println(nom + " (population de " + taille + " individus) exprime des propriétés émergentes : " +
                          "dynamique démographique, sélection naturelle, évolution");
    }

    public void evoluer() {
        System.out.println(nom + " évolue au fil des générations");
    }
}

class Ecosysteme extends NiveauBiologique {
    private String biome;

    public Ecosysteme(String nom, String biome) {
        super(nom);
        this.biome = biome;
    }

    @Override
    public void comportementEmergent() {
        System.out.println(nom + " (écosystème de type " + biome + ") exprime des propriétés émergentes : " +
                          "cycles biogéochimiques, résilience, succession écologique");
    }

    public void recyclerNutriments() {
        System.out.println(nom + " recycle les nutriments à travers les chaînes trophiques");
    }
}

public class HierarchieBiologique {
    public static void main(String[] args) {
        Cellule neurone = new Cellule("Neurone pyramidal", "neurone");
        Cellule astrocyte = new Cellule("Astrocyte", "glie");

        Tissu tissuNerveux = new Tissu("Cortex cérébral", "traitement de l'information");
        tissuNerveux.ajouterComposant(neurone);
        tissuNerveux.ajouterComposant(astrocyte);

        Organe cerveau = new Organe("Cerveau", "nerveux");
        cerveau.ajouterComposant(tissuNerveux);

        Organisme humain = new Organisme("Homo sapiens 1", "Homo sapiens");
        humain.ajouterComposant(cerveau);

        Population populationHumaine = new Population("Population humaine locale", 1000);
        populationHumaine.ajouterComposant(humain);

        Ecosysteme foret = new Ecosysteme("Forêt tempérée", "forêt");
        foret.ajouterComposant(populationHumaine);

        System.out.println("=== Propriétés émergentes à chaque niveau ===");
        neurone.comportementEmergent();
        tissuNerveux.comportementEmergent();
        cerveau.comportementEmergent();
        humain.comportementEmergent();
        populationHumaine.comportementEmergent();
        foret.comportementEmergent();

        System.out.println("\n=== Propagation de la maintenance ===");
        foret.maintenir();

        System.out.println("\n=== Interactions entre niveaux ===");
        neurone.interagir(astrocyte);
        cerveau.interagir(tissuNerveux);
        foret.interagir(populationHumaine);
    }
}
