package antforaging;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class AntForagingSimulation extends JFrame {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int GRID_SIZE = 80;
    private static final int CELL_SIZE = 10;
    private static final int ANT_COUNT = 100;
    private static final double PHEROMONE_EVAPORATION_RATE = 0.995;
    private static final double PHEROMONE_DEPOSIT = 1000.0;

    private Cell[][] grid;
    private List<Ant> ants;
    private Point nestLocation;
    private Point foodLocation;
    private Timer timer;
    private int foodCollected = 0;

    public AntForagingSimulation() {
        setTitle("Recherche de nourriture par les fourmis");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initializeSimulation();

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawSimulation(g);
            }
        };
        panel.setBackground(Color.WHITE);
        add(panel);

        timer = new Timer(16, e -> {
            updateSimulation();
            repaint();
        });
        timer.start();

        setVisible(true);
    }

    private void initializeSimulation() {
        grid = new Cell[GRID_SIZE][GRID_SIZE];
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) grid[x][y] = new Cell();
        }

        nestLocation = new Point(GRID_SIZE / 4, GRID_SIZE / 2);
        foodLocation = new Point(3 * GRID_SIZE / 4, GRID_SIZE / 2);

        grid[nestLocation.x][nestLocation.y].isNest = true;
        grid[foodLocation.x][foodLocation.y].isFood = true;
        grid[foodLocation.x][foodLocation.y].foodAmount = 1000;

        Random random = new Random();
        for (int i = 0; i < GRID_SIZE * GRID_SIZE / 20; i++) {
            int x = random.nextInt(GRID_SIZE);
            int y = random.nextInt(GRID_SIZE);
            if (!grid[x][y].isNest && !grid[x][y].isFood) {
                grid[x][y].isObstacle = true;
            }
        }

        ants = new ArrayList<>();
        for (int i = 0; i < ANT_COUNT; i++) {
            ants.add(new Ant(nestLocation.x, nestLocation.y));
        }
    }

    private void updateSimulation() {
        for (Ant ant : ants) ant.move();

        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                grid[x][y].homePheromone *= PHEROMONE_EVAPORATION_RATE;
                grid[x][y].foodPheromone *= PHEROMONE_EVAPORATION_RATE;
            }
        }
    }

    private void drawSimulation(Graphics g) {
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                int screenX = x * CELL_SIZE;
                int screenY = y * CELL_SIZE;

                if (grid[x][y].homePheromone > 0.1) {
                    int intensity = Math.min(255, (int)(grid[x][y].homePheromone * 0.5));
                    g.setColor(new Color(0, 0, intensity));
                    g.fillRect(screenX, screenY, CELL_SIZE, CELL_SIZE);
                }

                if (grid[x][y].foodPheromone > 0.1) {
                    int intensity = Math.min(255, (int)(grid[x][y].foodPheromone * 0.5));
                    g.setColor(new Color(intensity, 0, 0));
                    g.fillRect(screenX, screenY, CELL_SIZE, CELL_SIZE);
                }

                if (grid[x][y].isObstacle) {
                    g.setColor(Color.DARK_GRAY);
                    g.fillRect(screenX, screenY, CELL_SIZE, CELL_SIZE);
                }

                if (grid[x][y].isNest) {
                    g.setColor(Color.BLUE);
                    g.fillOval(screenX, screenY, CELL_SIZE, CELL_SIZE);
                }

                if (grid[x][y].isFood && grid[x][y].foodAmount > 0) {
                    g.setColor(Color.GREEN);
                    g.fillRect(screenX, screenY, CELL_SIZE, CELL_SIZE);
                }
            }
        }

        g.setColor(Color.BLACK);
        for (Ant ant : ants) {
            int screenX = ant.x * CELL_SIZE;
            int screenY = ant.y * CELL_SIZE;
            if (ant.hasFood) {
                g.setColor(Color.RED);
                g.fillOval(screenX + 2, screenY + 2, CELL_SIZE - 4, CELL_SIZE - 4);
                g.setColor(Color.BLACK);
            } else {
                g.fillOval(screenX + 2, screenY + 2, CELL_SIZE - 4, CELL_SIZE - 4);
            }
        }

        g.setColor(Color.BLACK);
        g.drawString("Nourriture collectée: " + foodCollected, 10, 20);
    }

    private static class Cell {
        boolean isObstacle = false;
        boolean isNest = false;
        boolean isFood = false;
        int foodAmount = 0;
        double homePheromone = 0.0;
        double foodPheromone = 0.0;
    }

    private class Ant {
        int x, y;
        boolean hasFood = false;
        int direction;
        private final Random antRandom = new Random();

        public Ant(int x, int y) {
            this.x = x;
            this.y = y;
            this.direction = antRandom.nextInt(8);
        }

        public void move() {
            chooseDirection();

            int newX = x;
            int newY = y;

            switch (direction) {
                case 0: newY--; break;
                case 1: newX++; newY--; break;
                case 2: newX++; break;
                case 3: newX++; newY++; break;
                case 4: newY++; break;
                case 5: newX--; newY++; break;
                case 6: newX--; break;
                case 7: newX--; newY--; break;
            }

            if (newX < 0 || newX >= GRID_SIZE || newY < 0 || newY >= GRID_SIZE) {
                direction = (direction + 4) % 8;
                return;
            }

            if (grid[newX][newY].isObstacle) {
                direction = antRandom.nextInt(8);
                return;
            }

            if (hasFood) {
                grid[x][y].foodPheromone += PHEROMONE_DEPOSIT;
            } else {
                grid[x][y].homePheromone += PHEROMONE_DEPOSIT;
            }

            x = newX;
            y = newY;

            if (!hasFood && grid[x][y].isFood && grid[x][y].foodAmount > 0) {
                hasFood = true;
                grid[x][y].foodAmount--;
                direction = (direction + 4) % 8;
            }

            if (hasFood && grid[x][y].isNest) {
                hasFood = false;
                foodCollected++;
                direction = antRandom.nextInt(8);
            }
        }

        private void chooseDirection() {
            if (antRandom.nextDouble() < 0.1) {
                direction = antRandom.nextInt(8);
                return;
            }

            double[] weights = new double[8];
            double totalWeight = 0.0;

            for (int dir = 0; dir < 8; dir++) {
                int checkX = x, checkY = y;
                switch (dir) {
                    case 0: checkY--; break;
                    case 1: checkX++; checkY--; break;
                    case 2: checkX++; break;
                    case 3: checkX++; checkY++; break;
                    case 4: checkY++; break;
                    case 5: checkX--; checkY++; break;
                    case 6: checkX--; break;
                    case 7: checkX--; checkY--; break;
                }

                if (checkX < 0 || checkX >= GRID_SIZE || checkY < 0 || checkY >= GRID_SIZE ||
                    grid[checkX][checkY].isObstacle) {
                    weights[dir] = 0.0;
                    continue;
                }

                weights[dir] = 0.1 + (hasFood ? grid[checkX][checkY].homePheromone : grid[checkX][checkY].foodPheromone);
                totalWeight += weights[dir];
            }

            if (totalWeight <= 0.0) return;

            double randomValue = antRandom.nextDouble() * totalWeight;
            double cumulativeWeight = 0.0;

            for (int dir = 0; dir < 8; dir++) {
                cumulativeWeight += weights[dir];
                if (randomValue <= cumulativeWeight) {
                    direction = dir;
                    break;
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AntForagingSimulation::new);
    }
}
