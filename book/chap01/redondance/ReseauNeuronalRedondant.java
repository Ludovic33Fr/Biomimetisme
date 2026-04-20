package redondance;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class ReseauNeuronalRedondant extends JFrame {

    private static final int NB_NEURONES_ENTREE = 5;
    private static final int NB_NEURONES_CACHES = 10;
    private static final int NB_NEURONES_SORTIE = 3;

    private List<Neurone> neuronesEntree;
    private List<Neurone> neuronesCaches;
    private List<Neurone> neuronesSortie;
    private List<Connexion> connexions;

    private boolean simulationEnCours = false;
    private Random random = new Random();

    private JPanel panneauReseau;
    private JTextArea zoneLog;
    private Timer timer;

    private class Neurone {
        private int id;
        private String type;
        private double activation;
        private boolean endommage = false;
        private Point position;

        public Neurone(int id, String type) {
            this.id = id;
            this.type = type;
            this.activation = 0.0;
        }

        public void setPosition(int x, int y) {
            this.position = new Point(x, y);
        }

        public void activer(double valeur) {
            if (!endommage) {
                this.activation = valeur;
            }
        }

        public double getActivation() {
            return endommage ? 0.0 : activation;
        }

        public void endommager() {
            this.endommage = true;
            log("Neurone " + type + " " + id + " endommagé");
        }

        public void reparer() {
            this.endommage = false;
            log("Neurone " + type + " " + id + " réparé");
        }

        public boolean estEndommage() {
            return endommage;
        }
    }

    private class Connexion {
        private Neurone source;
        private Neurone destination;
        private double poids;
        private boolean endommage = false;

        public Connexion(Neurone source, Neurone destination, double poids) {
            this.source = source;
            this.destination = destination;
            this.poids = poids;
        }

        public void transmettre() {
            if (!endommage && !source.estEndommage() && !destination.estEndommage()) {
                double valeur = source.getActivation() * poids;
                destination.activer(destination.getActivation() + valeur);
            }
        }

        public void endommager() {
            this.endommage = true;
            log("Connexion entre " + source.type + " " + source.id +
                " et " + destination.type + " " + destination.id + " endommagée");
        }

        public void reparer() {
            this.endommage = false;
            log("Connexion entre " + source.type + " " + source.id +
                " et " + destination.type + " " + destination.id + " réparée");
        }

        public boolean estEndommage() {
            return endommage;
        }
    }

    public ReseauNeuronalRedondant() {
        setTitle("Simulation de Réseau Neuronal Redondant");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(1000, 700);

        initialiserReseau();

        panneauReseau = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                dessinerReseau(g);
            }
        };
        panneauReseau.setBackground(Color.WHITE);

        zoneLog = new JTextArea(10, 40);
        zoneLog.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(zoneLog);

        JPanel panneauControle = new JPanel();
        panneauControle.setLayout(new FlowLayout());

        JButton boutonDemarrer = new JButton("Démarrer");
        boutonDemarrer.addActionListener(e -> {
            if (!simulationEnCours) {
                simulationEnCours = true;
                boutonDemarrer.setText("Pause");
                timer.start();
                log("Simulation démarrée");
            } else {
                simulationEnCours = false;
                boutonDemarrer.setText("Démarrer");
                timer.stop();
                log("Simulation en pause");
            }
        });

        JButton boutonEndommager = new JButton("Endommager aléatoirement");
        boutonEndommager.addActionListener(e -> {
            endommagerAleatoirement();
            panneauReseau.repaint();
        });

        JButton boutonReparer = new JButton("Réparer tout");
        boutonReparer.addActionListener(e -> {
            reparerTout();
            panneauReseau.repaint();
        });

        JButton boutonReinitialiser = new JButton("Réinitialiser");
        boutonReinitialiser.addActionListener(e -> {
            simulationEnCours = false;
            boutonDemarrer.setText("Démarrer");
            timer.stop();
            initialiserReseau();
            log("Réseau réinitialisé");
            panneauReseau.repaint();
        });

        panneauControle.add(boutonDemarrer);
        panneauControle.add(boutonEndommager);
        panneauControle.add(boutonReparer);
        panneauControle.add(boutonReinitialiser);

        add(panneauReseau, BorderLayout.CENTER);
        add(scrollPane, BorderLayout.SOUTH);
        add(panneauControle, BorderLayout.NORTH);

        timer = new Timer(100, e -> {
            mettreAJourSimulation();
            panneauReseau.repaint();
        });

        setLocationRelativeTo(null);
        setVisible(true);

        log("Réseau neuronal initialisé avec redondance");
        log("Neurones d'entrée: " + NB_NEURONES_ENTREE);
        log("Neurones cachés: " + NB_NEURONES_CACHES + " (redondance)");
        log("Neurones de sortie: " + NB_NEURONES_SORTIE);
    }

    private void initialiserReseau() {
        neuronesEntree = new ArrayList<>();
        neuronesCaches = new ArrayList<>();
        neuronesSortie = new ArrayList<>();

        for (int i = 0; i < NB_NEURONES_ENTREE; i++) {
            Neurone neurone = new Neurone(i, "entree");
            neurone.setPosition(100, 100 + i * 100);
            neuronesEntree.add(neurone);
        }

        for (int i = 0; i < NB_NEURONES_CACHES; i++) {
            Neurone neurone = new Neurone(i, "cache");
            neurone.setPosition(400, 50 + i * 50);
            neuronesCaches.add(neurone);
        }

        for (int i = 0; i < NB_NEURONES_SORTIE; i++) {
            Neurone neurone = new Neurone(i, "sortie");
            neurone.setPosition(700, 150 + i * 150);
            neuronesSortie.add(neurone);
        }

        connexions = new ArrayList<>();

        for (Neurone source : neuronesEntree) {
            for (Neurone destination : neuronesCaches) {
                double poids = random.nextDouble() * 2 - 1;
                connexions.add(new Connexion(source, destination, poids));
            }
        }

        for (Neurone source : neuronesCaches) {
            for (Neurone destination : neuronesSortie) {
                double poids = random.nextDouble() * 2 - 1;
                connexions.add(new Connexion(source, destination, poids));
            }
        }
    }

    private void mettreAJourSimulation() {
        for (Neurone neurone : neuronesCaches) neurone.activer(0.0);
        for (Neurone neurone : neuronesSortie) neurone.activer(0.0);

        for (Neurone neurone : neuronesEntree) {
            neurone.activer(random.nextDouble());
        }

        for (Connexion connexion : connexions) {
            connexion.transmettre();
        }

        for (Neurone neurone : neuronesCaches) {
            if (!neurone.estEndommage()) {
                neurone.activer(sigmoide(neurone.getActivation()));
            }
        }
        for (Neurone neurone : neuronesSortie) {
            if (!neurone.estEndommage()) {
                neurone.activer(sigmoide(neurone.getActivation()));
            }
        }

        StringBuilder sb = new StringBuilder("Sorties: ");
        for (Neurone neurone : neuronesSortie) {
            sb.append(String.format("%.2f ", neurone.getActivation()));
        }
        log(sb.toString());
    }

    private double sigmoide(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private void endommagerAleatoirement() {
        int nbNeurones = (int)(neuronesCaches.size() * 0.3);
        for (int i = 0; i < nbNeurones; i++) {
            int index = random.nextInt(neuronesCaches.size());
            neuronesCaches.get(index).endommager();
        }

        int nbConnexions = (int)(connexions.size() * 0.2);
        for (int i = 0; i < nbConnexions; i++) {
            int index = random.nextInt(connexions.size());
            connexions.get(index).endommager();
        }

        log("Réseau endommagé: " + nbNeurones + " neurones et " + nbConnexions + " connexions");
    }

    private void reparerTout() {
        for (Neurone neurone : neuronesEntree) neurone.reparer();
        for (Neurone neurone : neuronesCaches) neurone.reparer();
        for (Neurone neurone : neuronesSortie) neurone.reparer();
        for (Connexion connexion : connexions) connexion.reparer();
        log("Réseau entièrement réparé");
    }

    private void dessinerReseau(Graphics g) {
        for (Connexion connexion : connexions) {
            if (connexion.estEndommage()) {
                g.setColor(Color.RED);
            } else {
                if (connexion.poids < 0) {
                    int intensite = (int)(255 * Math.min(1.0, Math.abs(connexion.poids)));
                    g.setColor(new Color(0, 0, intensite));
                } else {
                    int intensite = (int)(255 * Math.min(1.0, connexion.poids));
                    g.setColor(new Color(0, intensite, 0));
                }
            }

            if (!connexion.source.estEndommage() && !connexion.destination.estEndommage()) {
                g.drawLine(connexion.source.position.x, connexion.source.position.y,
                          connexion.destination.position.x, connexion.destination.position.y);
            }
        }

        for (List<Neurone> couche : Arrays.asList(neuronesEntree, neuronesCaches, neuronesSortie)) {
            for (Neurone neurone : couche) {
                if (neurone.estEndommage()) {
                    g.setColor(Color.RED);
                } else {
                    int intensite = (int)(255 * neurone.getActivation());
                    g.setColor(new Color(intensite, intensite, intensite));
                }

                int rayon = 20;
                g.fillOval(neurone.position.x - rayon, neurone.position.y - rayon,
                          2 * rayon, 2 * rayon);

                g.setColor(Color.BLACK);
                g.drawOval(neurone.position.x - rayon, neurone.position.y - rayon,
                          2 * rayon, 2 * rayon);

                g.drawString(neurone.type + " " + neurone.id,
                            neurone.position.x - 15, neurone.position.y + 30);
            }
        }

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 14));

        int neuronesEndommages = 0;
        for (Neurone neurone : neuronesCaches) {
            if (neurone.estEndommage()) neuronesEndommages++;
        }

        int connexionsEndommagees = 0;
        for (Connexion connexion : connexions) {
            if (connexion.estEndommage()) connexionsEndommagees++;
        }

        g.drawString("Neurones endommagés: " + neuronesEndommages + "/" + neuronesCaches.size() +
                    " (" + (int)(100.0 * neuronesEndommages / neuronesCaches.size()) + "%)",
                    50, 30);

        g.drawString("Connexions endommagées: " + connexionsEndommagees + "/" + connexions.size() +
                    " (" + (int)(100.0 * connexionsEndommagees / connexions.size()) + "%)",
                    50, 50);
    }

    private void log(String message) {
        zoneLog.append(message + "\n");
        zoneLog.setCaretPosition(zoneLog.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ReseauNeuronalRedondant::new);
    }
}
