package ecosysteme;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class EcosystemeSimple extends JFrame {

    private static final int LARGEUR_MONDE = 800;
    private static final int HAUTEUR_MONDE = 600;
    private static final int NB_PRODUCTEURS_INITIAL = 200;
    private static final int NB_HERBIVORES_INITIAL = 50;
    private static final int NB_CARNIVORES_INITIAL = 10;
    private static final double TAUX_CROISSANCE_PRODUCTEUR = 0.1;
    private static final double ENERGIE_INITIALE_HERBIVORE = 100;
    private static final double ENERGIE_INITIALE_CARNIVORE = 200;
    private static final double COUT_REPRODUCTION_HERBIVORE = 50;
    private static final double COUT_REPRODUCTION_CARNIVORE = 100;
    private static final double GAIN_ENERGIE_HERBIVORE = 20;
    private static final double GAIN_ENERGIE_CARNIVORE = 80;

    private Random random = new Random();
    private List<Producteur> producteurs = new ArrayList<>();
    private List<Herbivore> herbivores = new ArrayList<>();
    private List<Carnivore> carnivores = new ArrayList<>();
    private int generation = 0;

    private List<Integer> historiqueProducteurs = new ArrayList<>();
    private List<Integer> historiqueHerbivores = new ArrayList<>();
    private List<Integer> historiqueCarnivores = new ArrayList<>();

    private JPanel panneauMonde;
    private JPanel panneauGraphique;
    private Timer timer;

    private class Producteur {
        private double x, y;
        private double biomasse;

        public Producteur(double x, double y) {
            this.x = x;
            this.y = y;
            this.biomasse = 10 + random.nextDouble() * 10;
        }

        public void croitre() {
            biomasse *= (1 + TAUX_CROISSANCE_PRODUCTEUR * random.nextDouble());
            biomasse = Math.min(biomasse, 50);
        }

        public double consommer(double quantite) {
            double consommee = Math.min(biomasse, quantite);
            biomasse -= consommee;
            return consommee;
        }

        public boolean estEpuise() {
            return biomasse <= 0;
        }
    }

    private abstract class Consommateur {
        protected double x, y;
        protected double vitesse;
        protected double direction;
        protected double energie;

        public Consommateur(double x, double y, double energie) {
            this.x = x;
            this.y = y;
            this.energie = energie;
            this.vitesse = 2 + random.nextDouble() * 3;
            this.direction = random.nextDouble() * 2 * Math.PI;
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

        public double distance(double autreX, double autreY) {
            double dx = Math.min(Math.abs(x - autreX), LARGEUR_MONDE - Math.abs(x - autreX));
            double dy = Math.min(Math.abs(y - autreY), HAUTEUR_MONDE - Math.abs(y - autreY));
            return Math.sqrt(dx * dx + dy * dy);
        }

        public abstract Consommateur seReproduire();
        public abstract void manger();
    }

    private class Herbivore extends Consommateur {
        public Herbivore(double x, double y) {
            super(x, y, ENERGIE_INITIALE_HERBIVORE);
        }

        @Override
        public Herbivore seReproduire() {
            if (energie > COUT_REPRODUCTION_HERBIVORE) {
                energie -= COUT_REPRODUCTION_HERBIVORE;
                return new Herbivore(x, y);
            }
            return null;
        }

        @Override
        public void manger() {
            Producteur cible = null;
            double distanceMin = Double.MAX_VALUE;

            for (Producteur p : producteurs) {
                double d = distance(p.x, p.y);
                if (d < distanceMin) {
                    distanceMin = d;
                    cible = p;
                }
            }

            if (cible != null && distanceMin < 50) {
                double dx = cible.x - x;
                double dy = cible.y - y;
                if (Math.abs(dx) > LARGEUR_MONDE / 2) dx = -Math.signum(dx) * (LARGEUR_MONDE - Math.abs(dx));
                if (Math.abs(dy) > HAUTEUR_MONDE / 2) dy = -Math.signum(dy) * (HAUTEUR_MONDE - Math.abs(dy));
                direction = Math.atan2(dy, dx);

                if (distanceMin < vitesse) {
                    double consommee = cible.consommer(10);
                    energie += consommee * GAIN_ENERGIE_HERBIVORE / 10.0;
                }
            }
        }
    }

    private class Carnivore extends Consommateur {
        public Carnivore(double x, double y) {
            super(x, y, ENERGIE_INITIALE_CARNIVORE);
            this.vitesse = 3 + random.nextDouble() * 4;
        }

        @Override
        public Carnivore seReproduire() {
            if (energie > COUT_REPRODUCTION_CARNIVORE) {
                energie -= COUT_REPRODUCTION_CARNIVORE;
                return new Carnivore(x, y);
            }
            return null;
        }

        @Override
        public void manger() {
            Herbivore cible = null;
            double distanceMin = Double.MAX_VALUE;

            for (Herbivore h : herbivores) {
                double d = distance(h.x, h.y);
                if (d < distanceMin) {
                    distanceMin = d;
                    cible = h;
                }
            }

            if (cible != null && distanceMin < 100) {
                double dx = cible.x - x;
                double dy = cible.y - y;
                if (Math.abs(dx) > LARGEUR_MONDE / 2) dx = -Math.signum(dx) * (LARGEUR_MONDE - Math.abs(dx));
                if (Math.abs(dy) > HAUTEUR_MONDE / 2) dy = -Math.signum(dy) * (HAUTEUR_MONDE - Math.abs(dy));
                direction = Math.atan2(dy, dx);

                if (distanceMin < vitesse) {
                    energie += GAIN_ENERGIE_CARNIVORE;
                    herbivores.remove(cible);
                }
            }
        }
    }

    public EcosystemeSimple() {
        setTitle("Simulation d'Écosystème Simple");
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
        panneauMonde.setBackground(Color.DARK_GRAY);

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
            historiqueProducteurs.clear();
            historiqueHerbivores.clear();
            historiqueCarnivores.clear();
            generation = 0;
            panneauMonde.repaint();
            panneauGraphique.repaint();
        });
        JButton boutonPerturber = new JButton("Perturbation 90%");
        boutonPerturber.addActionListener(e -> {
            perturber(0.9);
            panneauMonde.repaint();
        });
        panneauControle.add(boutonDemarrer);
        panneauControle.add(boutonReinitialiser);
        panneauControle.add(boutonPerturber);

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
        producteurs.clear();
        herbivores.clear();
        carnivores.clear();

        for (int i = 0; i < NB_PRODUCTEURS_INITIAL; i++) {
            producteurs.add(new Producteur(random.nextDouble() * LARGEUR_MONDE, random.nextDouble() * HAUTEUR_MONDE));
        }
        for (int i = 0; i < NB_HERBIVORES_INITIAL; i++) {
            herbivores.add(new Herbivore(random.nextDouble() * LARGEUR_MONDE, random.nextDouble() * HAUTEUR_MONDE));
        }
        for (int i = 0; i < NB_CARNIVORES_INITIAL; i++) {
            carnivores.add(new Carnivore(random.nextDouble() * LARGEUR_MONDE, random.nextDouble() * HAUTEUR_MONDE));
        }
    }

    public void mettreAJourSimulation() {
        generation++;

        Iterator<Producteur> iterProd = producteurs.iterator();
        while (iterProd.hasNext()) {
            Producteur p = iterProd.next();
            p.croitre();
            if (p.estEpuise()) iterProd.remove();
        }
        if (producteurs.size() < NB_PRODUCTEURS_INITIAL * 2 && random.nextDouble() < 0.1) {
             producteurs.add(new Producteur(random.nextDouble() * LARGEUR_MONDE, random.nextDouble() * HAUTEUR_MONDE));
        }

        List<Herbivore> nouveauxHerbivores = new ArrayList<>();
        Iterator<Herbivore> iterHerb = herbivores.iterator();
        while (iterHerb.hasNext()) {
            Herbivore h = iterHerb.next();
            h.deplacer();
            h.manger();
            if (h.estMort()) {
                iterHerb.remove();
            } else {
                Herbivore bebe = h.seReproduire();
                if (bebe != null) nouveauxHerbivores.add(bebe);
            }
        }
        herbivores.addAll(nouveauxHerbivores);

        List<Carnivore> nouveauxCarnivores = new ArrayList<>();
        Iterator<Carnivore> iterCarn = carnivores.iterator();
        while (iterCarn.hasNext()) {
            Carnivore c = iterCarn.next();
            c.deplacer();
            c.manger();
            if (c.estMort()) {
                iterCarn.remove();
            } else {
                Carnivore bebe = c.seReproduire();
                if (bebe != null) nouveauxCarnivores.add(bebe);
            }
        }
        carnivores.addAll(nouveauxCarnivores);

        if (generation % 10 == 0) {
            historiqueProducteurs.add(producteurs.size());
            historiqueHerbivores.add(herbivores.size());
            historiqueCarnivores.add(carnivores.size());

            if (historiqueProducteurs.size() > 100) {
                historiqueProducteurs.remove(0);
                historiqueHerbivores.remove(0);
                historiqueCarnivores.remove(0);
            }
        }
    }

    /**
     * Perturbation : tue un pourcentage aléatoire d'herbivores pour observer
     * la résilience de l'écosystème.
     */
    public void perturber(double pourcentageHerbivoresTues) {
        int nbTues = (int) (herbivores.size() * pourcentageHerbivoresTues);
        Collections.shuffle(herbivores);
        for (int i = 0; i < nbTues && !herbivores.isEmpty(); i++) {
            herbivores.remove(0);
        }
        System.out.println("*** PERTURBATION: " + nbTues + " herbivores tués (" +
                           (int)(pourcentageHerbivoresTues * 100) + "%) ***");
    }

    private void dessinerMonde(Graphics g) {
        g.setColor(new Color(0, 150, 0));
        for (Producteur p : producteurs) {
            int taille = (int) (p.biomasse / 5);
            g.fillRect((int) p.x - taille / 2, (int) p.y - taille / 2, taille, taille);
        }

        g.setColor(Color.BLUE);
        for (Herbivore h : herbivores) {
            g.fillOval((int) h.x - 4, (int) h.y - 4, 8, 8);
        }

        g.setColor(Color.RED);
        for (Carnivore c : carnivores) {
            g.fillOval((int) c.x - 5, (int) c.y - 5, 10, 10);
        }

        g.setColor(Color.WHITE);
        g.drawString("Génération: " + generation, 10, 20);
        g.drawString("Producteurs: " + producteurs.size(), 10, 40);
        g.drawString("Herbivores: " + herbivores.size(), 10, 60);
        g.drawString("Carnivores: " + carnivores.size(), 10, 80);
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

        if (historiqueProducteurs.isEmpty()) return;

        int maxPopulation = 0;
        for (int pop : historiqueProducteurs) maxPopulation = Math.max(maxPopulation, pop);
        for (int pop : historiqueHerbivores) maxPopulation = Math.max(maxPopulation, pop);
        for (int pop : historiqueCarnivores) maxPopulation = Math.max(maxPopulation, pop);
        maxPopulation = Math.max(maxPopulation, 100);

        int xStep = (largeur - 70) / Math.max(1, historiqueProducteurs.size() - 1);

        g.setColor(new Color(0, 150, 0));
        for (int i = 0; i < historiqueProducteurs.size() - 1; i++) {
            int x1 = 50 + i * xStep;
            int y1 = hauteur - 30 - (int)(historiqueProducteurs.get(i) * (hauteur - 50.0) / maxPopulation);
            int x2 = 50 + (i + 1) * xStep;
            int y2 = hauteur - 30 - (int)(historiqueProducteurs.get(i + 1) * (hauteur - 50.0) / maxPopulation);
            g.drawLine(x1, y1, x2, y2);
        }

        g.setColor(Color.BLUE);
        for (int i = 0; i < historiqueHerbivores.size() - 1; i++) {
            int x1 = 50 + i * xStep;
            int y1 = hauteur - 30 - (int)(historiqueHerbivores.get(i) * (hauteur - 50.0) / maxPopulation);
            int x2 = 50 + (i + 1) * xStep;
            int y2 = hauteur - 30 - (int)(historiqueHerbivores.get(i + 1) * (hauteur - 50.0) / maxPopulation);
            g.drawLine(x1, y1, x2, y2);
        }

        g.setColor(Color.RED);
        for (int i = 0; i < historiqueCarnivores.size() - 1; i++) {
            int x1 = 50 + i * xStep;
            int y1 = hauteur - 30 - (int)(historiqueCarnivores.get(i) * (hauteur - 50.0) / maxPopulation);
            int x2 = 50 + (i + 1) * xStep;
            int y2 = hauteur - 30 - (int)(historiqueCarnivores.get(i + 1) * (hauteur - 50.0) / maxPopulation);
            g.drawLine(x1, y1, x2, y2);
        }

        g.setColor(new Color(0, 150, 0)); g.fillRect(largeur - 150, 20, 15, 15);
        g.setColor(Color.BLUE); g.fillRect(largeur - 150, 40, 15, 15);
        g.setColor(Color.RED); g.fillRect(largeur - 150, 60, 15, 15);
        g.setColor(Color.BLACK);
        g.drawString("Producteurs", largeur - 130, 33);
        g.drawString("Herbivores", largeur - 130, 53);
        g.drawString("Carnivores", largeur - 130, 73);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(EcosystemeSimple::new);
    }
}
