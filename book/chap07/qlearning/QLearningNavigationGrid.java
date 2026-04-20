package qlearning;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

public class QLearningNavigationGrid extends JPanel implements ActionListener {

    private static final int LARGEUR_GRILLE = 10;
    private static final int HAUTEUR_GRILLE = 10;
    private static final int TAILLE_CELLULE = 50;

    private static final int VIDE = 0;
    private static final int OBSTACLE = 1;
    private static final int OBJECTIF = 2;
    private static final int AGENT = 3;

    private static final double ALPHA = 0.1;
    private static final double GAMMA = 0.9;
    private static final double EPSILON_INITIAL = 1.0;
    private static final double EPSILON_FINAL = 0.1;
    private static final int EPISODES_MAX = 1000;

    private static final int HAUT = 0;
    private static final int DROITE = 1;
    private static final int BAS = 2;
    private static final int GAUCHE = 3;
    private static final int NB_ACTIONS = 4;

    private int[][] grille;
    private int agentX, agentY;
    private int objectifX, objectifY;

    private double[][][] qTable;

    private int episodeCourant;
    private double epsilon;
    private int etapesCourantes;
    private int etapesTotal;
    private boolean apprentissageTermine;

    private List<Integer> etapesParEpisode;

    private Timer timer;
    private JButton btnDemarrer;
    private JButton btnReinitialiser;
    private JLabel lblEpisode, lblEtapes, lblEpsilon;

    public QLearningNavigationGrid() {
        grille = new int[LARGEUR_GRILLE][HAUTEUR_GRILLE];
        qTable = new double[LARGEUR_GRILLE][HAUTEUR_GRILLE][NB_ACTIONS];
        etapesParEpisode = new ArrayList<>();

        setPreferredSize(new Dimension(LARGEUR_GRILLE * TAILLE_CELLULE,
                                      HAUTEUR_GRILLE * TAILLE_CELLULE + 50));
        setBackground(Color.WHITE);

        JPanel panelControles = new JPanel();
        btnDemarrer = new JButton("Démarrer");
        btnReinitialiser = new JButton("Réinitialiser");
        lblEpisode = new JLabel("Épisode: 0/" + EPISODES_MAX);
        lblEtapes = new JLabel("Étapes: 0");
        lblEpsilon = new JLabel("Epsilon: 1.0");

        panelControles.add(btnDemarrer);
        panelControles.add(btnReinitialiser);
        panelControles.add(lblEpisode);
        panelControles.add(lblEtapes);
        panelControles.add(lblEpsilon);

        setLayout(new BorderLayout());
        add(panelControles, BorderLayout.SOUTH);

        btnDemarrer.addActionListener(e -> {
            if (timer.isRunning()) {
                timer.stop();
                btnDemarrer.setText("Démarrer");
            } else {
                timer.start();
                btnDemarrer.setText("Pause");
            }
        });

        btnReinitialiser.addActionListener(e -> reinitialiser());

        timer = new Timer(50, this);
        reinitialiser();
    }

    private void reinitialiser() {
        timer.stop();
        btnDemarrer.setText("Démarrer");
        btnDemarrer.setEnabled(true);

        for (int x = 0; x < LARGEUR_GRILLE; x++) {
            for (int y = 0; y < HAUTEUR_GRILLE; y++) grille[x][y] = VIDE;
        }

        Random random = new Random();
        int nbObstacles = LARGEUR_GRILLE * HAUTEUR_GRILLE / 5;
        for (int i = 0; i < nbObstacles; i++) {
            int x, y;
            do {
                x = random.nextInt(LARGEUR_GRILLE);
                y = random.nextInt(HAUTEUR_GRILLE);
            } while (grille[x][y] != VIDE);
            grille[x][y] = OBSTACLE;
        }

        do {
            agentX = random.nextInt(LARGEUR_GRILLE);
            agentY = random.nextInt(HAUTEUR_GRILLE);
        } while (grille[agentX][agentY] != VIDE);
        grille[agentX][agentY] = AGENT;

        do {
            objectifX = random.nextInt(LARGEUR_GRILLE);
            objectifY = random.nextInt(HAUTEUR_GRILLE);
        } while (grille[objectifX][objectifY] != VIDE);
        grille[objectifX][objectifY] = OBJECTIF;

        for (int x = 0; x < LARGEUR_GRILLE; x++) {
            for (int y = 0; y < HAUTEUR_GRILLE; y++) {
                for (int a = 0; a < NB_ACTIONS; a++) qTable[x][y][a] = 0.0;
            }
        }

        episodeCourant = 0;
        epsilon = EPSILON_INITIAL;
        etapesCourantes = 0;
        etapesTotal = 0;
        etapesParEpisode.clear();
        apprentissageTermine = false;

        lblEpisode.setText("Épisode: 0/" + EPISODES_MAX);
        lblEtapes.setText("Étapes: 0");
        lblEpsilon.setText("Epsilon: " + String.format("%.2f", epsilon));

        repaint();
    }

    private void executerEtapeQLearning() {
        if (apprentissageTermine) return;

        if (grille[agentX][agentY] == OBJECTIF) {
            etapesParEpisode.add(etapesCourantes);
            episodeCourant++;

            epsilon = Math.max(EPSILON_FINAL,
                              EPSILON_INITIAL - (EPSILON_INITIAL - EPSILON_FINAL) *
                              episodeCourant / EPISODES_MAX);

            if (episodeCourant >= EPISODES_MAX) {
                apprentissageTermine = true;
                timer.stop();
                btnDemarrer.setText("Terminé");
                btnDemarrer.setEnabled(false);
                System.out.println("Apprentissage terminé!");
                afficherStatistiques();
                return;
            }

            grille[agentX][agentY] = VIDE;
            Random random = new Random();
            do {
                agentX = random.nextInt(LARGEUR_GRILLE);
                agentY = random.nextInt(HAUTEUR_GRILLE);
            } while (grille[agentX][agentY] != VIDE);
            grille[agentX][agentY] = AGENT;

            etapesCourantes = 0;

            lblEpisode.setText("Épisode: " + episodeCourant + "/" + EPISODES_MAX);
            lblEtapes.setText("Étapes: " + etapesCourantes);
            lblEpsilon.setText("Epsilon: " + String.format("%.2f", epsilon));

            repaint();
            return;
        }

        int action;
        if (Math.random() < epsilon) {
            action = new Random().nextInt(NB_ACTIONS);
        } else {
            action = getMeilleureAction(agentX, agentY);
        }

        int nouveauX = agentX;
        int nouveauY = agentY;

        switch (action) {
            case HAUT: if (agentY > 0) nouveauY--; break;
            case DROITE: if (agentX < LARGEUR_GRILLE - 1) nouveauX++; break;
            case BAS: if (agentY < HAUTEUR_GRILLE - 1) nouveauY++; break;
            case GAUCHE: if (agentX > 0) nouveauX--; break;
        }

        if (grille[nouveauX][nouveauY] == OBSTACLE) {
            nouveauX = agentX;
            nouveauY = agentY;
        }

        double recompense;
        if (nouveauX == objectifX && nouveauY == objectifY) {
            recompense = 100.0;
        } else if (nouveauX == agentX && nouveauY == agentY) {
            recompense = -5.0;
        } else {
            recompense = -0.1;
        }

        int meilleureActionSuivante = getMeilleureAction(nouveauX, nouveauY);
        double maxQSuivant = qTable[nouveauX][nouveauY][meilleureActionSuivante];

        qTable[agentX][agentY][action] += ALPHA *
            (recompense + GAMMA * maxQSuivant - qTable[agentX][agentY][action]);

        grille[agentX][agentY] = VIDE;
        agentX = nouveauX;
        agentY = nouveauY;
        grille[agentX][agentY] = AGENT;

        etapesCourantes++;
        etapesTotal++;

        lblEtapes.setText("Étapes: " + etapesCourantes);

        repaint();
    }

    private int getMeilleureAction(int x, int y) {
        int meilleureAction = 0;
        double meilleureValeur = qTable[x][y][0];

        for (int a = 1; a < NB_ACTIONS; a++) {
            if (qTable[x][y][a] > meilleureValeur) {
                meilleureValeur = qTable[x][y][a];
                meilleureAction = a;
            }
        }

        return meilleureAction;
    }

    private void afficherStatistiques() {
        System.out.println("Nombre total d'épisodes: " + episodeCourant);
        System.out.println("Nombre total d'étapes: " + etapesTotal);

        double moyenneEtapes = etapesParEpisode.stream()
                              .mapToInt(Integer::intValue)
                              .average()
                              .orElse(0.0);
        System.out.println("Moyenne d'étapes par épisode: " + moyenneEtapes);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        executerEtapeQLearning();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        for (int x = 0; x < LARGEUR_GRILLE; x++) {
            for (int y = 0; y < HAUTEUR_GRILLE; y++) {
                switch (grille[x][y]) {
                    case VIDE: g2d.setColor(Color.WHITE); break;
                    case OBSTACLE: g2d.setColor(Color.BLACK); break;
                    case OBJECTIF: g2d.setColor(Color.GREEN); break;
                    case AGENT: g2d.setColor(Color.RED); break;
                }

                g2d.fillRect(x * TAILLE_CELLULE, y * TAILLE_CELLULE, TAILLE_CELLULE, TAILLE_CELLULE);

                g2d.setColor(Color.GRAY);
                g2d.drawRect(x * TAILLE_CELLULE, y * TAILLE_CELLULE, TAILLE_CELLULE, TAILLE_CELLULE);

                if (apprentissageTermine && grille[x][y] != OBSTACLE && grille[x][y] != OBJECTIF) {
                    int meilleureAction = getMeilleureAction(x, y);
                    g2d.setColor(Color.BLUE);

                    int centreX = x * TAILLE_CELLULE + TAILLE_CELLULE / 2;
                    int centreY = y * TAILLE_CELLULE + TAILLE_CELLULE / 2;
                    int taille = TAILLE_CELLULE / 3;

                    switch (meilleureAction) {
                        case HAUT:
                            g2d.drawLine(centreX, centreY, centreX, centreY - taille);
                            break;
                        case DROITE:
                            g2d.drawLine(centreX, centreY, centreX + taille, centreY);
                            break;
                        case BAS:
                            g2d.drawLine(centreX, centreY, centreX, centreY + taille);
                            break;
                        case GAUCHE:
                            g2d.drawLine(centreX, centreY, centreX - taille, centreY);
                            break;
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Q-Learning Navigation Grid");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new QLearningNavigationGrid());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
