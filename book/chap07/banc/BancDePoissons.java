package banc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

public class BancDePoissons extends JPanel implements ActionListener {

    private List<Poisson> poissons;
    private int largeur, hauteur;
    private Timer timer;

    private static final int NOMBRE_POISSONS = 100;
    private static final int RAYON_PERCEPTION = 50;
    private static final double POIDS_COHESION = 0.01;
    private static final double POIDS_ALIGNEMENT = 0.1;
    private static final double POIDS_SEPARATION = 0.1;
    private static final double VITESSE_MAX = 4.0;

    public BancDePoissons(int largeur, int hauteur) {
        this.largeur = largeur;
        this.hauteur = hauteur;
        this.poissons = new ArrayList<>();

        Random random = new Random();
        for (int i = 0; i < NOMBRE_POISSONS; i++) {
            double x = random.nextDouble() * largeur;
            double y = random.nextDouble() * hauteur;
            double vx = random.nextDouble() * 2 - 1;
            double vy = random.nextDouble() * 2 - 1;

            double norme = Math.sqrt(vx * vx + vy * vy);
            if (norme > 0) {
                vx = vx / norme * VITESSE_MAX / 2;
                vy = vy / norme * VITESSE_MAX / 2;
            }

            poissons.add(new Poisson(x, y, vx, vy));
        }

        setPreferredSize(new Dimension(largeur, hauteur));
        setBackground(Color.BLUE);
        timer = new Timer(50, this);
        timer.start();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        for (Poisson poisson : poissons) {
            List<Poisson> voisins = getVoisins(poisson);

            if (!voisins.isEmpty()) {
                Vector2D cohesion = calculerCohesion(poisson, voisins);
                Vector2D alignement = calculerAlignement(poisson, voisins);
                Vector2D separation = calculerSeparation(poisson, voisins);

                poisson.vx += cohesion.x * POIDS_COHESION +
                             alignement.x * POIDS_ALIGNEMENT +
                             separation.x * POIDS_SEPARATION;

                poisson.vy += cohesion.y * POIDS_COHESION +
                             alignement.y * POIDS_ALIGNEMENT +
                             separation.y * POIDS_SEPARATION;

                double norme = Math.sqrt(poisson.vx * poisson.vx + poisson.vy * poisson.vy);
                if (norme > VITESSE_MAX) {
                    poisson.vx = poisson.vx / norme * VITESSE_MAX;
                    poisson.vy = poisson.vy / norme * VITESSE_MAX;
                }
            }

            poisson.x += poisson.vx;
            poisson.y += poisson.vy;

            if (poisson.x < 0) poisson.x += largeur;
            if (poisson.y < 0) poisson.y += hauteur;
            if (poisson.x >= largeur) poisson.x -= largeur;
            if (poisson.y >= hauteur) poisson.y -= hauteur;
        }

        repaint();
    }

    private List<Poisson> getVoisins(Poisson poisson) {
        List<Poisson> voisins = new ArrayList<>();

        for (Poisson autre : poissons) {
            if (autre != poisson) {
                double dx = Math.min(
                    Math.abs(autre.x - poisson.x),
                    Math.min(Math.abs(autre.x + largeur - poisson.x), Math.abs(autre.x - largeur - poisson.x))
                );

                double dy = Math.min(
                    Math.abs(autre.y - poisson.y),
                    Math.min(Math.abs(autre.y + hauteur - poisson.y), Math.abs(autre.y - hauteur - poisson.y))
                );

                double distance = Math.sqrt(dx * dx + dy * dy);

                if (distance < RAYON_PERCEPTION) voisins.add(autre);
            }
        }

        return voisins;
    }

    private Vector2D calculerCohesion(Poisson poisson, List<Poisson> voisins) {
        double centreX = 0, centreY = 0;

        for (Poisson voisin : voisins) {
            double dx = voisin.x - poisson.x;
            if (dx > largeur / 2.0) dx -= largeur;
            if (dx < -largeur / 2.0) dx += largeur;

            double dy = voisin.y - poisson.y;
            if (dy > hauteur / 2.0) dy -= hauteur;
            if (dy < -hauteur / 2.0) dy += hauteur;

            centreX += poisson.x + dx;
            centreY += poisson.y + dy;
        }

        centreX /= voisins.size();
        centreY /= voisins.size();

        return new Vector2D(centreX - poisson.x, centreY - poisson.y);
    }

    private Vector2D calculerAlignement(Poisson poisson, List<Poisson> voisins) {
        double vxMoyen = 0, vyMoyen = 0;

        for (Poisson voisin : voisins) {
            vxMoyen += voisin.vx;
            vyMoyen += voisin.vy;
        }

        vxMoyen /= voisins.size();
        vyMoyen /= voisins.size();

        return new Vector2D(vxMoyen - poisson.vx, vyMoyen - poisson.vy);
    }

    private Vector2D calculerSeparation(Poisson poisson, List<Poisson> voisins) {
        double separationX = 0, separationY = 0;

        for (Poisson voisin : voisins) {
            double dx = poisson.x - voisin.x;
            if (dx > largeur / 2.0) dx -= largeur;
            if (dx < -largeur / 2.0) dx += largeur;

            double dy = poisson.y - voisin.y;
            if (dy > hauteur / 2.0) dy -= hauteur;
            if (dy < -hauteur / 2.0) dy += hauteur;

            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance > 0) {
                separationX += dx / (distance * distance);
                separationY += dy / (distance * distance);
            }
        }

        return new Vector2D(separationX, separationY);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(Color.WHITE);

        for (Poisson poisson : poissons) {
            double angle = Math.atan2(poisson.vy, poisson.vx);

            int[] xPoints = new int[3];
            int[] yPoints = new int[3];

            xPoints[0] = (int) (poisson.x + 10 * Math.cos(angle));
            yPoints[0] = (int) (poisson.y + 10 * Math.sin(angle));
            xPoints[1] = (int) (poisson.x + 5 * Math.cos(angle + Math.PI * 0.8));
            yPoints[1] = (int) (poisson.y + 5 * Math.sin(angle + Math.PI * 0.8));
            xPoints[2] = (int) (poisson.x + 5 * Math.cos(angle - Math.PI * 0.8));
            yPoints[2] = (int) (poisson.y + 5 * Math.sin(angle - Math.PI * 0.8));

            g2d.fillPolygon(xPoints, yPoints, 3);
        }
    }

    private static class Poisson {
        double x, y, vx, vy;

        Poisson(double x, double y, double vx, double vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        }
    }

    private static class Vector2D {
        double x, y;
        Vector2D(double x, double y) { this.x = x; this.y = y; }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Banc de poissons auto-organisé");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(new BancDePoissons(800, 600));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
