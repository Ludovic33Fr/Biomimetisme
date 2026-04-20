package emergence;

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

/**
 * Outil d'analyse de l'émergence dans un système multi-agents.
 * Visualise en temps réel un ensemble d'agents simples et mesure
 * les métriques d'émergence : entropie, cohésion, formation de clusters.
 */
public class EmergenceAnalysisTool extends JFrame {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int AGENT_COUNT = 150;

    private final List<Agent> agents;
    private Timer timer;
    private int step = 0;
    private JLabel statsLabel;

    public EmergenceAnalysisTool() {
        setTitle("Analyse d'émergence");
        setSize(WIDTH, HEIGHT + 80);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        agents = new ArrayList<>();
        Random random = new Random();
        for (int i = 0; i < AGENT_COUNT; i++) {
            agents.add(new Agent(random.nextDouble() * WIDTH, random.nextDouble() * HEIGHT,
                    random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1));
        }

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                draw((Graphics2D) g);
            }
        };
        canvas.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        canvas.setBackground(Color.BLACK);
        add(canvas, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        JButton startBtn = new JButton("Démarrer");
        statsLabel = new JLabel("Pas 0 | Entropie: - | Clusters: -");
        controls.add(startBtn);
        controls.add(statsLabel);
        add(controls, BorderLayout.SOUTH);

        startBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (timer.isRunning()) { timer.stop(); startBtn.setText("Démarrer"); }
                else { timer.start(); startBtn.setText("Pause"); }
            }
        });

        timer = new Timer(50, e -> {
            step++;
            updateAgents();
            double entropy = computeEntropy();
            int clusters = countClusters();
            statsLabel.setText(String.format("Pas %d | Entropie: %.3f | Clusters: %d",
                    step, entropy, clusters));
            canvas.repaint();
        });

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void updateAgents() {
        // Règles locales simples : alignement + cohésion
        for (Agent a : agents) {
            double sumVx = 0, sumVy = 0, sumX = 0, sumY = 0;
            int count = 0;

            for (Agent b : agents) {
                if (a == b) continue;
                double dx = b.x - a.x, dy = b.y - a.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 50) {
                    sumVx += b.vx; sumVy += b.vy;
                    sumX += b.x; sumY += b.y;
                    count++;
                }
            }

            if (count > 0) {
                a.vx += (sumVx / count - a.vx) * 0.1;
                a.vy += (sumVy / count - a.vy) * 0.1;
                a.vx += (sumX / count - a.x) * 0.001;
                a.vy += (sumY / count - a.y) * 0.001;
            }

            double speed = Math.sqrt(a.vx * a.vx + a.vy * a.vy);
            if (speed > 3) { a.vx = a.vx / speed * 3; a.vy = a.vy / speed * 3; }

            a.x += a.vx; a.y += a.vy;
            if (a.x < 0) a.x += WIDTH; if (a.x > WIDTH) a.x -= WIDTH;
            if (a.y < 0) a.y += HEIGHT; if (a.y > HEIGHT) a.y -= HEIGHT;
        }
    }

    private double computeEntropy() {
        // Entropie spatiale simple basée sur une grille de cellules
        int gridSize = 10;
        int[][] cells = new int[gridSize][gridSize];
        for (Agent a : agents) {
            int cx = Math.min(gridSize - 1, (int) (a.x / WIDTH * gridSize));
            int cy = Math.min(gridSize - 1, (int) (a.y / HEIGHT * gridSize));
            cells[cx][cy]++;
        }

        double entropy = 0;
        for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
                if (cells[x][y] > 0) {
                    double p = (double) cells[x][y] / AGENT_COUNT;
                    entropy -= p * Math.log(p);
                }
            }
        }
        return entropy;
    }

    private int countClusters() {
        // Comptage simplifié : nombre de cellules non vides dans une grille coarse
        int gridSize = 8;
        boolean[][] occupied = new boolean[gridSize][gridSize];
        for (Agent a : agents) {
            int cx = Math.min(gridSize - 1, (int) (a.x / WIDTH * gridSize));
            int cy = Math.min(gridSize - 1, (int) (a.y / HEIGHT * gridSize));
            occupied[cx][cy] = true;
        }

        int count = 0;
        for (int x = 0; x < gridSize; x++) {
            for (int y = 0; y < gridSize; y++) {
                if (occupied[x][y]) count++;
            }
        }
        return count;
    }

    private void draw(Graphics2D g) {
        g.setColor(Color.WHITE);
        for (Agent a : agents) {
            g.fillOval((int) a.x - 2, (int) a.y - 2, 4, 4);
        }
    }

    private static class Agent {
        double x, y, vx, vy;
        Agent(double x, double y, double vx, double vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        }
    }

    public static void main(String[] args) {
        new EmergenceAnalysisTool();
    }
}
