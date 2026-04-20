package coevolution;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class EcosystemeCoevolution extends JFrame {

    private static final int LARGEUR_MONDE = 800;
    private static final int HAUTEUR_MONDE = 600;
    private static final int NB_PROIES_INITIAL = 100;
    private static final int NB_PREDATEURS_INITIAL = 20;
    private static final int TAILLE_GENOME = 10;
    private static final double TAUX_MUTATION = 0.1;
    private static final double ENERGIE_INITIALE_PROIE = 100;
    private static final double ENERGIE_INITIALE_PREDATEUR = 200;
    private static final double COUT_REPRODUCTION_PROIE = 50;
    private static final double COUT_REPRODUCTION_PREDATEUR = 100;
    private static final double GAIN_ENERGIE_PREDATION = 80;

    private Random random = new Random();
    private List<Proie> proies = new ArrayList<>();
    private List<Predateur> predateurs = new ArrayList<>();
    private int generation = 0;

    private List<Integer> historiqueProies = new ArrayList<>();
    private List<Integer> historiquePredateurs = new ArrayList<>();
    private List<Double> historiqueVitesseProies = new ArrayList<>();
    private List<Double> historiqueVitessePredateurs = new ArrayList<>();

    private JPanel panneauMonde;
    private JPanel panneauGraphique;
    private Timer timer;

    private abstract class Organisme {
        protected double x, y;
        protected double vitesse;
        protected double direction;
        protected double energie;
        protected boolean[] genome;

        public Organisme(double x, double y, boolean[] genome, double energie) {
            this.x = x;
            this.y = y;
            this.genome = genome;
            this.energie = energie;
            this.direction = random.nextDouble() * 2 * Math.PI;

            int compteur = 0;
            for (boolean gene : genome) if (gene) compteur++;
            this.vitesse = 1.0 + compteur;
        }

        public void deplacer() {
            direction += (random.nextDouble() - 0.5) * 0.5;
            x += Math.cos(direction) * vitesse;
            y += Math.sin(direction) * vitesse;

            if (x < 0) x += LARGEUR_MONDE;
            if (x >= LARGEUR_MONDE) x -= LARGEUR_MONDE;
            if (y < 0) y += HAUTEUR_MONDE;
            if (y >= HAUTEUR_MONDE) y -= HAUTEUR_MONDE;

            energie -= 0.1 * vitesse;
        }

        public boolean estMort() {
            return energie <= 0;
        }

        public double distance(Organisme autre) {
            double dx = Math.min(Math.abs(x - autre.x), LARGEUR_MONDE - Math.abs(x - autre.x));
            double dy = Math.min(Math.abs(y - autre.y), HAUTEUR_MONDE - Math.abs(y - autre.y));
            return Math.sqrt(dx * dx + dy * dy);
        }

        protected boolean[] genererGenomeMute() {
            boolean[] nouveauGenome = new boolean[TAILLE_GENOME];
            for (int i = 0; i < TAILLE_GENOME; i++) {
                nouveauGenome[i] = (random.nextDouble() < TAUX_MUTATION) ? !genome[i] : genome[i];
            }
            return nouveauGenome;
        }
    }

    private class Proie extends Organisme {
        public Proie(double x, double y, boolean[] genome) {
            super(x, y, genome, ENERGIE_INITIALE_PROIE);
        }

        public Proie seReproduire() {
            if (energie > COUT_REPRODUCTION_PROIE) {
                energie -= COUT_REPRODUCTION_PROIE;
                return new Proie(x, y, genererGenomeMute());
            }
            return null;
        }

        public void trouverNourriture() {
            if (random.nextDouble() < 0.1) energie += 20;
        }
    }

    private class Predateur extends Organisme {
        public Predateur(double x, double y, boolean[] genome) {
            super(x, y, genome, ENERGIE_INITIALE_PREDATEUR);
        }

        public Predateur seReproduire() {
            if (energie > COUT_REPRODUCTION_PREDATEUR) {
                energie -= COUT_REPRODUCTION_PREDATEUR;
                return new Predateur(x, y, genererGenomeMute());
            }
            return null;
        }

        public void chasser(List<Proie> proies) {
            Proie proiePlusProche = null;
            double distanceMin = Double.MAX_VALUE;

            for (Proie proie : proies) {
                double d = distance(proie);
                if (d < distanceMin) {
                    distanceMin = d;
                    proiePlusProche = proie;
                }
            }

            if (proiePlusProche != null && distanceMin < 100) {
                double dx = proiePlusProche.x - x;
                double dy = proiePlusProche.y - y;

                if (Math.abs(dx) > LARGEUR_MONDE / 2) {
                    dx = -Math.signum(dx) * (LARGEUR_MONDE - Math.abs(dx));
                }
                if (Math.abs(dy) > HAUTEUR_MONDE / 2) {
                    dy = -Math.signum(dy) * (HAUTEUR_MONDE - Math.abs(dy));
                }

                direction = Math.atan2(dy, dx);

                if (distanceMin < vitesse) {
                    energie += GAIN_ENERGIE_PREDATION;
                    proies.remove(proiePlusProche);
                }
            }
        }
    }

    public EcosystemeCoevolution() {
        setTitle("Écosystème avec Co-évolution Prédateurs-Proies");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(1000, 700);

        initialiserEcosysteme();

        panneauMonde = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                dessinerMonde(g);
            }
        };
        panneauMonde.setPreferredSize(new Dimension(LARGEUR_MONDE, HAUTEUR_MONDE));
        panneauMonde.setBackground(Color.WHITE);

        panneauGraphique = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                dessinerGraphiques(g);
            }
        };
        panneauGraphique.setPreferredSize(new Dimension(1000, 200));
        panneauGraphique.setBackground(Color.LIGHT_GRAY);

        JPanel panneauControle = new JPanel();

        JButton boutonDemarrer = new JButton("Démarrer");
        boutonDemarrer.addActionListener(e -> {
            if (timer.isRunning()) {
                timer.stop();
                boutonDemarrer.setText("Démarrer");
            } else {
                timer.start();
                boutonDemarrer.setText("Pause");
            }
        });

        JButton boutonReinitialiser = new JButton("Réinitialiser");
        boutonReinitialiser.addActionListener(e -> {
            timer.stop();
            boutonDemarrer.setText("Démarrer");
            initialiserEcosysteme();
            historiqueProies.clear();
            historiquePredateurs.clear();
            historiqueVitesseProies.clear();
            historiqueVitessePredateurs.clear();
            generation = 0;
            panneauMonde.repaint();
            panneauGraphique.repaint();
        });

        panneauControle.add(boutonDemarrer);
        panneauControle.add(boutonReinitialiser);

        add(panneauMonde, BorderLayout.CENTER);
        add(panneauGraphique, BorderLayout.SOUTH);
        add(panneauControle, BorderLayout.NORTH);

        timer = new Timer(50, e -> {
            mettreAJourSimulation();
            panneauMonde.repaint();
            panneauGraphique.repaint();
        });

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initialiserEcosysteme() {
        proies.clear();
        predateurs.clear();

        for (int i = 0; i < NB_PROIES_INITIAL; i++) {
            double x = random.nextDouble() * LARGEUR_MONDE;
            double y = random.nextDouble() * HAUTEUR_MONDE;
            boolean[] genome = new boolean[TAILLE_GENOME];
            for (int j = 0; j < TAILLE_GENOME; j++) genome[j] = random.nextBoolean();
            proies.add(new Proie(x, y, genome));
        }

        for (int i = 0; i < NB_PREDATEURS_INITIAL; i++) {
            double x = random.nextDouble() * LARGEUR_MONDE;
            double y = random.nextDouble() * HAUTEUR_MONDE;
            boolean[] genome = new boolean[TAILLE_GENOME];
            for (int j = 0; j < TAILLE_GENOME; j++) genome[j] = random.nextBoolean();
            predateurs.add(new Predateur(x, y, genome));
        }
    }

    private void mettreAJourSimulation() {
        generation++;

        List<Proie> nouvellesProies = new ArrayList<>();
        List<Predateur> nouveauxPredateurs = new ArrayList<>();

        Iterator<Proie> iterProies = proies.iterator();
        while (iterProies.hasNext()) {
            Proie proie = iterProies.next();
            proie.deplacer();
            proie.trouverNourriture();

            if (proie.estMort()) {
                iterProies.remove();
                continue;
            }

            Proie nouvelleProie = proie.seReproduire();
            if (nouvelleProie != null) nouvellesProies.add(nouvelleProie);
        }

        proies.addAll(nouvellesProies);

        Iterator<Predateur> iterPredateurs = predateurs.iterator();
        while (iterPredateurs.hasNext()) {
            Predateur predateur = iterPredateurs.next();
            predateur.deplacer();
            predateur.chasser(proies);

            if (predateur.estMort()) {
                iterPredateurs.remove();
                continue;
            }

            Predateur nouveauPredateur = predateur.seReproduire();
            if (nouveauPredateur != null) nouveauxPredateurs.add(nouveauPredateur);
        }

        predateurs.addAll(nouveauxPredateurs);

        if (generation % 10 == 0) {
            historiqueProies.add(proies.size());
            historiquePredateurs.add(predateurs.size());

            double vitesseMoyenneProies = 0;
            for (Proie proie : proies) vitesseMoyenneProies += proie.vitesse;
            vitesseMoyenneProies /= Math.max(1, proies.size());
            historiqueVitesseProies.add(vitesseMoyenneProies);

            double vitesseMoyennePredateurs = 0;
            for (Predateur predateur : predateurs) vitesseMoyennePredateurs += predateur.vitesse;
            vitesseMoyennePredateurs /= Math.max(1, predateurs.size());
            historiqueVitessePredateurs.add(vitesseMoyennePredateurs);

            if (historiqueProies.size() > 100) {
                historiqueProies.remove(0);
                historiquePredateurs.remove(0);
                historiqueVitesseProies.remove(0);
                historiqueVitessePredateurs.remove(0);
            }
        }
    }

    private void dessinerMonde(Graphics g) {
        g.setColor(Color.GREEN);
        for (Proie proie : proies) {
            int taille = 4 + (int)(proie.vitesse / 2);
            g.fillOval((int)proie.x - taille/2, (int)proie.y - taille/2, taille, taille);
        }

        g.setColor(Color.RED);
        for (Predateur predateur : predateurs) {
            int taille = 6 + (int)(predateur.vitesse / 2);
            g.fillOval((int)predateur.x - taille/2, (int)predateur.y - taille/2, taille, taille);
        }

        g.setColor(Color.BLACK);
        g.drawString("Génération: " + generation, 10, 20);
        g.drawString("Proies: " + proies.size(), 10, 40);
        g.drawString("Prédateurs: " + predateurs.size(), 10, 60);

        if (!proies.isEmpty()) {
            double vitesseMoyenneProies = 0;
            for (Proie proie : proies) vitesseMoyenneProies += proie.vitesse;
            vitesseMoyenneProies /= proies.size();
            g.drawString("Vitesse moyenne proies: " + String.format("%.2f", vitesseMoyenneProies), 10, 80);
        }

        if (!predateurs.isEmpty()) {
            double vitesseMoyennePredateurs = 0;
            for (Predateur predateur : predateurs) vitesseMoyennePredateurs += predateur.vitesse;
            vitesseMoyennePredateurs /= predateurs.size();
            g.drawString("Vitesse moyenne prédateurs: " + String.format("%.2f", vitesseMoyennePredateurs), 10, 100);
        }
    }

    private void dessinerGraphiques(Graphics g) {
        int largeur = panneauGraphique.getWidth();
        int hauteur = panneauGraphique.getHeight();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, largeur, hauteur);

        g.setColor(Color.BLACK);
        g.drawLine(50, hauteur - 30, largeur - 20, hauteur - 30);
        g.drawLine(50, 20, 50, hauteur - 30);

        g.drawString("Génération", largeur / 2, hauteur - 10);
        g.drawString("Population", 10, hauteur / 2);

        if (historiqueProies.isEmpty()) return;

        int maxPopulation = 0;
        for (int pop : historiqueProies) maxPopulation = Math.max(maxPopulation, pop);
        for (int pop : historiquePredateurs) maxPopulation = Math.max(maxPopulation, pop);
        maxPopulation = Math.max(maxPopulation, 100);

        int xStep = (largeur - 70) / Math.max(1, historiqueProies.size() - 1);

        g.setColor(Color.GREEN);
        for (int i = 0; i < historiqueProies.size() - 1; i++) {
            int x1 = 50 + i * xStep;
            int y1 = hauteur - 30 - (int)(historiqueProies.get(i) * (hauteur - 50) / maxPopulation);
            int x2 = 50 + (i + 1) * xStep;
            int y2 = hauteur - 30 - (int)(historiqueProies.get(i + 1) * (hauteur - 50) / maxPopulation);
            g.drawLine(x1, y1, x2, y2);
        }

        g.setColor(Color.RED);
        for (int i = 0; i < historiquePredateurs.size() - 1; i++) {
            int x1 = 50 + i * xStep;
            int y1 = hauteur - 30 - (int)(historiquePredateurs.get(i) * (hauteur - 50) / maxPopulation);
            int x2 = 50 + (i + 1) * xStep;
            int y2 = hauteur - 30 - (int)(historiquePredateurs.get(i + 1) * (hauteur - 50) / maxPopulation);
            g.drawLine(x1, y1, x2, y2);
        }

        g.setColor(Color.GREEN);
        g.fillRect(largeur - 120, 20, 15, 15);
        g.setColor(Color.BLACK);
        g.drawString("Proies", largeur - 100, 33);

        g.setColor(Color.RED);
        g.fillRect(largeur - 120, 40, 15, 15);
        g.setColor(Color.BLACK);
        g.drawString("Prédateurs", largeur - 100, 53);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(EcosystemeCoevolution::new);
    }
}
