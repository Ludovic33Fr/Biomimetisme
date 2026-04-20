package automate;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Random;

public class JeuDeLaVie extends JFrame {
    private static final int TAILLE_CELLULE = 10;
    private static final int LARGEUR = 80;
    private static final int HAUTEUR = 60;

    private boolean[][] grille = new boolean[LARGEUR][HAUTEUR];
    private boolean[][] prochaineGrille = new boolean[LARGEUR][HAUTEUR];
    private boolean enExecution = false;
    private int generation = 0;

    private JPanel panneauGrille;
    private JLabel labelGeneration;
    private Timer timer;

    public JeuDeLaVie() {
        setTitle("Jeu de la Vie - Auto-organisation et Émergence");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        panneauGrille = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                dessinerGrille(g);
            }
        };
        panneauGrille.setPreferredSize(new Dimension(LARGEUR * TAILLE_CELLULE, HAUTEUR * TAILLE_CELLULE));
        panneauGrille.setBackground(Color.WHITE);

        panneauGrille.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!enExecution) {
                    int x = e.getX() / TAILLE_CELLULE;
                    int y = e.getY() / TAILLE_CELLULE;
                    if (x >= 0 && x < LARGEUR && y >= 0 && y < HAUTEUR) {
                        grille[x][y] = !grille[x][y];
                        panneauGrille.repaint();
                    }
                }
            }
        });

        add(panneauGrille, BorderLayout.CENTER);

        JPanel panneauControle = new JPanel();

        JButton boutonDemarrer = new JButton("Démarrer");
        boutonDemarrer.addActionListener(e -> {
            if (!enExecution) {
                enExecution = true;
                boutonDemarrer.setText("Pause");
                timer.start();
            } else {
                enExecution = false;
                boutonDemarrer.setText("Démarrer");
                timer.stop();
            }
        });

        JButton boutonReinitialiser = new JButton("Réinitialiser");
        boutonReinitialiser.addActionListener(e -> {
            enExecution = false;
            boutonDemarrer.setText("Démarrer");
            timer.stop();
            initialiserGrille(false);
            generation = 0;
            labelGeneration.setText("Génération: 0");
            panneauGrille.repaint();
        });

        JButton boutonAleatoire = new JButton("Aléatoire");
        boutonAleatoire.addActionListener(e -> {
            initialiserGrille(true);
            generation = 0;
            labelGeneration.setText("Génération: 0");
            panneauGrille.repaint();
        });

        labelGeneration = new JLabel("Génération: 0");

        panneauControle.add(boutonDemarrer);
        panneauControle.add(boutonReinitialiser);
        panneauControle.add(boutonAleatoire);
        panneauControle.add(labelGeneration);

        add(panneauControle, BorderLayout.SOUTH);

        timer = new Timer(100, e -> {
            calculerProchaineGeneration();
            generation++;
            labelGeneration.setText("Génération: " + generation);
            panneauGrille.repaint();
        });

        initialiserGrille(false);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initialiserGrille(boolean aleatoire) {
        Random random = new Random();
        for (int x = 0; x < LARGEUR; x++) {
            for (int y = 0; y < HAUTEUR; y++) {
                grille[x][y] = aleatoire && random.nextDouble() < 0.2;
            }
        }
    }

    private void dessinerGrille(Graphics g) {
        g.setColor(Color.BLACK);
        for (int x = 0; x < LARGEUR; x++) {
            for (int y = 0; y < HAUTEUR; y++) {
                if (grille[x][y]) {
                    g.fillRect(x * TAILLE_CELLULE, y * TAILLE_CELLULE,
                              TAILLE_CELLULE, TAILLE_CELLULE);
                }
            }
        }

        g.setColor(Color.LIGHT_GRAY);
        for (int x = 0; x <= LARGEUR; x++) {
            g.drawLine(x * TAILLE_CELLULE, 0, x * TAILLE_CELLULE, HAUTEUR * TAILLE_CELLULE);
        }
        for (int y = 0; y <= HAUTEUR; y++) {
            g.drawLine(0, y * TAILLE_CELLULE, LARGEUR * TAILLE_CELLULE, y * TAILLE_CELLULE);
        }
    }

    private void calculerProchaineGeneration() {
        for (int x = 0; x < LARGEUR; x++) {
            for (int y = 0; y < HAUTEUR; y++) {
                int voisinsVivants = compterVoisinsVivants(x, y);
                if (grille[x][y]) {
                    prochaineGrille[x][y] = voisinsVivants == 2 || voisinsVivants == 3;
                } else {
                    prochaineGrille[x][y] = voisinsVivants == 3;
                }
            }
        }

        for (int x = 0; x < LARGEUR; x++) {
            for (int y = 0; y < HAUTEUR; y++) {
                grille[x][y] = prochaineGrille[x][y];
            }
        }
    }

    private int compterVoisinsVivants(int x, int y) {
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                int nx = (x + dx + LARGEUR) % LARGEUR;
                int ny = (y + dy + HAUTEUR) % HAUTEUR;
                if (grille[nx][ny]) count++;
            }
        }
        return count;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(JeuDeLaVie::new);
    }
}
