package flocking;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class FlockingSimulation extends JFrame {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int BOID_COUNT = 100;

    private List<Boid> boids;
    private Timer timer;

    public FlockingSimulation() {
        setTitle("Simulation de vol en essaim");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initializeBoids();

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawBoids(g);
            }
        };
        panel.setBackground(Color.BLACK);
        add(panel);

        timer = new Timer(16, e -> {
            updateBoids();
            repaint();
        });
        timer.start();

        setVisible(true);
    }

    private void initializeBoids() {
        boids = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < BOID_COUNT; i++) {
            double x = random.nextDouble() * WIDTH;
            double y = random.nextDouble() * HEIGHT;
            double vx = random.nextDouble() * 2 - 1;
            double vy = random.nextDouble() * 2 - 1;
            boids.add(new Boid(x, y, vx, vy));
        }
    }

    private void updateBoids() {
        for (Boid boid : boids) {
            Vector separation = calculateSeparation(boid);
            Vector alignment = calculateAlignment(boid);
            Vector cohesion = calculateCohesion(boid);

            boid.applyForce(separation.multiply(1.5));
            boid.applyForce(alignment);
            boid.applyForce(cohesion);

            boid.update();

            if (boid.position.x < 0) boid.position.x += WIDTH;
            if (boid.position.y < 0) boid.position.y += HEIGHT;
            if (boid.position.x > WIDTH) boid.position.x -= WIDTH;
            if (boid.position.y > HEIGHT) boid.position.y -= HEIGHT;
        }
    }

    private Vector calculateSeparation(Boid boid) {
        Vector steer = new Vector(0, 0);
        int count = 0;
        double desiredSeparation = 25;

        for (Boid other : boids) {
            if (other == boid) continue;
            double distance = boid.position.distance(other.position);
            if (distance < desiredSeparation && distance > 0) {
                Vector diff = new Vector(boid.position.x - other.position.x, boid.position.y - other.position.y);
                diff.normalize();
                diff.divide(distance);
                steer.add(diff);
                count++;
            }
        }

        if (count > 0) steer.divide(count);

        if (steer.magnitude() > 0) {
            steer.normalize();
            steer.multiply(boid.maxSpeed);
            Vector result = new Vector(steer.x - boid.velocity.x, steer.y - boid.velocity.y);
            result.limit(boid.maxForce);
            return result;
        }
        return steer;
    }

    private Vector calculateAlignment(Boid boid) {
        Vector sum = new Vector(0, 0);
        int count = 0;
        double neighborDistance = 50;

        for (Boid other : boids) {
            if (other == boid) continue;
            double distance = boid.position.distance(other.position);
            if (distance < neighborDistance) {
                sum.add(other.velocity);
                count++;
            }
        }

        if (count > 0) {
            sum.divide(count);
            sum.normalize();
            sum.multiply(boid.maxSpeed);

            Vector steer = new Vector(sum.x - boid.velocity.x, sum.y - boid.velocity.y);
            steer.limit(boid.maxForce);
            return steer;
        }

        return new Vector(0, 0);
    }

    private Vector calculateCohesion(Boid boid) {
        Vector sum = new Vector(0, 0);
        int count = 0;
        double neighborDistance = 50;

        for (Boid other : boids) {
            if (other == boid) continue;
            double distance = boid.position.distance(other.position);
            if (distance < neighborDistance) {
                sum.add(other.position);
                count++;
            }
        }

        if (count > 0) {
            sum.divide(count);
            return boid.seek(sum);
        }

        return new Vector(0, 0);
    }

    private void drawBoids(Graphics g) {
        g.setColor(Color.WHITE);

        for (Boid boid : boids) {
            int x = (int) boid.position.x;
            int y = (int) boid.position.y;

            double angle = Math.atan2(boid.velocity.y, boid.velocity.x);
            int size = 5;

            int[] xPoints = new int[3];
            int[] yPoints = new int[3];

            xPoints[0] = x + (int) (size * 2 * Math.cos(angle));
            yPoints[0] = y + (int) (size * 2 * Math.sin(angle));
            xPoints[1] = x + (int) (size * Math.cos(angle + Math.PI * 3 / 4));
            yPoints[1] = y + (int) (size * Math.sin(angle + Math.PI * 3 / 4));
            xPoints[2] = x + (int) (size * Math.cos(angle - Math.PI * 3 / 4));
            yPoints[2] = y + (int) (size * Math.sin(angle - Math.PI * 3 / 4));

            g.fillPolygon(xPoints, yPoints, 3);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FlockingSimulation::new);
    }

    private static class Boid {
        Vector position;
        Vector velocity;
        Vector acceleration;
        double maxSpeed = 2;
        double maxForce = 0.03;

        public Boid(double x, double y, double vx, double vy) {
            position = new Vector(x, y);
            velocity = new Vector(vx, vy);
            acceleration = new Vector(0, 0);
        }

        public void update() {
            velocity.add(acceleration);
            velocity.limit(maxSpeed);
            position.add(velocity);
            acceleration.multiply(0);
        }

        public void applyForce(Vector force) {
            acceleration.add(force);
        }

        public Vector seek(Vector target) {
            Vector desired = new Vector(target.x - position.x, target.y - position.y);
            desired.normalize();
            desired.multiply(maxSpeed);
            Vector steer = new Vector(desired.x - velocity.x, desired.y - velocity.y);
            steer.limit(maxForce);
            return steer;
        }
    }

    private static class Vector {
        double x, y;

        public Vector(double x, double y) { this.x = x; this.y = y; }

        public double magnitude() { return Math.sqrt(x * x + y * y); }

        public void normalize() {
            double mag = magnitude();
            if (mag > 0) { x /= mag; y /= mag; }
        }

        public Vector add(Vector v) { x += v.x; y += v.y; return this; }
        public Vector multiply(double s) { x *= s; y *= s; return this; }

        public Vector divide(double s) {
            if (s > 0) { x /= s; y /= s; }
            return this;
        }

        public void limit(double max) {
            if (magnitude() > max) { normalize(); multiply(max); }
        }

        public double distance(Vector v) {
            double dx = x - v.x;
            double dy = y - v.y;
            return Math.sqrt(dx * dx + dy * dy);
        }
    }
}
