package autoorg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class AutoOrganisationVegetale extends JFrame {

    private static final int TAILLE_GRILLE = 100;
    private static final int TAILLE_CELLULE = 6;

    private int[][] grille = new int[TAILLE_GRILLE][TAILLE_GRILLE];
    private int[][] prochaineGrille = new int[TAILLE_GRILLE][TAILLE_GRILLE];

    private Random random = new Random();
    private JPanel panneauGrille;
    private Timer timer;
    private int generation = 0;

    public AutoOrganisationVegetale() {
        setTitle("Auto-organisation Spatiale Végétale");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        panneauGrille = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                dessinerGrille(g);
            }
        };
        panneauGrille.setPreferredSize(new Dimension(TAILLE_GRILLE * TAILLE_CELLULE, TAILLE_GRILLE * TAILLE_CELLULE));
        panneauGrille.setBackground(Color.WHITE);
        add(panneauGrille, BorderLayout.CENTER);

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
            initialiserGrille();
            generation = 0;
            panneauGrille.repaint();
        });
        panneauControle.add(boutonDemarrer);
        panneauControle.add(boutonReinitialiser);
        add(panneauControle, BorderLayout.SOUTH);

        initialiserGrille();

        timer = new Timer(100, e -> {
            calculerProchaineGeneration();
            generation++;
            panneauGrille.repaint();
        });

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initialiserGrille() {
        for (int x = 0; x < TAILLE_GRILLE; x++) {
            for (int y = 0; y < TAILLE_GRILLE; y++) {
                double r = random.nextDouble();
                if (r < 0.1) grille[x][y] = 1;
                else if (r < 0.2) grille[x][y] = 2;
                else if (r < 0.3) grille[x][y] = 3;
                else grille[x][y] = 0;
            }
        }
    }

    private void dessinerGrille(Graphics g) {
        for (int x = 0; x < TAILLE_GRILLE; x++) {
            for (int y = 0; y < TAILLE_GRILLE; y++) {
                switch (grille[x][y]) {
                    case 1: g.setColor(Color.RED); break;
                    case 2: g.setColor(Color.GREEN); break;
                    case 3: g.setColor(Color.BLUE); break;
                    default: g.setColor(Color.WHITE);
                }
                g.fillRect(x * TAILLE_CELLULE, y * TAILLE_CELLULE, TAILLE_CELLULE, TAILLE_CELLULE);
            }
        }
        g.setColor(Color.BLACK);
        g.drawString("Génération: " + generation, 10, 20);
    }

    private void calculerProchaineGeneration() {
        for (int x = 0; x < TAILLE_GRILLE; x++) {
            for (int y = 0; y < TAILLE_GRILLE; y++) {
                int etatActuel = grille[x][y];
                int[] voisins = compterVoisins(x, y);

                prochaineGrille[x][y] = etatActuel;

                if (etatActuel == 0) {
                    double probaA = 0, probaB = 0, probaC = 0;
                    if (voisins[1] > 0) probaA = 0.1 * voisins[1];
                    if (voisins[2] > 0) probaB = 0.1 * voisins[2];
                    if (voisins[3] > 0) probaC = 0.1 * voisins[3];

                    if (voisins[2] > 0) probaA *= 1.5;
                    if (voisins[3] > 0) probaB *= 1.5;
                    if (voisins[1] > 0) probaC *= 1.5;

                    if (random.nextDouble() < probaA) prochaineGrille[x][y] = 1;
                    else if (random.nextDouble() < probaB) prochaineGrille[x][y] = 2;
                    else if (random.nextDouble() < probaC) prochaineGrille[x][y] = 3;

                } else {
                    double probaMort = 0.01;
                    if (etatActuel == 1 && voisins[3] > 0) probaMort += 0.2 * voisins[3];
                    if (etatActuel == 2 && voisins[1] > 0) probaMort += 0.2 * voisins[1];
                    if (etatActuel == 3 && voisins[2] > 0) probaMort += 0.2 * voisins[2];

                    if (random.nextDouble() < probaMort) {
                        prochaineGrille[x][y] = 0;
                    }
                }
            }
        }

        for (int x = 0; x < TAILLE_GRILLE; x++) {
            System.arraycopy(prochaineGrille[x], 0, grille[x], 0, TAILLE_GRILLE);
        }
    }

    private int[] compterVoisins(int x, int y) {
        int[] compte = new int[4];
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = (x + dx + TAILLE_GRILLE) % TAILLE_GRILLE;
                int ny = (y + dy + TAILLE_GRILLE) % TAILLE_GRILLE;
                compte[grille[nx][ny]]++;
            }
        }
        return compte;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AutoOrganisationVegetale::new);
    }
}
