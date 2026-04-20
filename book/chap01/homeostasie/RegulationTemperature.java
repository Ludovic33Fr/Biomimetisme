package homeostasie;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class RegulationTemperature extends JFrame {

    private static final double TEMPERATURE_CIBLE = 37.0;
    private static final double TEMPERATURE_ENVIRONNEMENT = 22.0;
    private static final double TAUX_TRANSFERT_CHALEUR = 0.1;

    private double temperatureCorporelle = 37.0;
    private double tauxMetabolisme = 1.0;
    private double tauxTranspiration = 0.0;
    private double tauxFrisson = 0.0;
    private boolean vasodilationActive = false;
    private boolean vasoconstrictionActive = false;

    private double perturbationExterne = 0.0;

    private List<Double> historiqueTemperature = new ArrayList<>();
    private List<String> historiqueActions = new ArrayList<>();

    private JPanel panneauGraphique;
    private JTextArea zoneLog;
    private JTextField champPerturbation;
    private Timer timer;

    public RegulationTemperature() {
        setTitle("Simulation de Régulation de la Température Corporelle");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        setSize(800, 600);

        panneauGraphique = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                dessinerGraphique(g);
            }
        };
        panneauGraphique.setBackground(Color.WHITE);

        zoneLog = new JTextArea(10, 40);
        zoneLog.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(zoneLog);

        JPanel panneauControle = new JPanel();
        panneauControle.setLayout(new FlowLayout());

        JLabel labelPerturbation = new JLabel("Perturbation (°C):");
        champPerturbation = new JTextField("0.0", 5);

        JButton boutonAppliquer = new JButton("Appliquer");
        boutonAppliquer.addActionListener(e -> {
            try {
                perturbationExterne = Double.parseDouble(champPerturbation.getText());
                log("Perturbation externe appliquée: " + perturbationExterne + " °C");
            } catch (NumberFormatException ex) {
                log("Valeur de perturbation invalide");
            }
        });

        JButton boutonReinitialiser = new JButton("Réinitialiser");
        boutonReinitialiser.addActionListener(e -> {
            temperatureCorporelle = 37.0;
            tauxMetabolisme = 1.0;
            tauxTranspiration = 0.0;
            tauxFrisson = 0.0;
            vasodilationActive = false;
            vasoconstrictionActive = false;
            perturbationExterne = 0.0;
            champPerturbation.setText("0.0");
            historiqueTemperature.clear();
            historiqueActions.clear();
            log("Système réinitialisé");
            panneauGraphique.repaint();
        });

        panneauControle.add(labelPerturbation);
        panneauControle.add(champPerturbation);
        panneauControle.add(boutonAppliquer);
        panneauControle.add(boutonReinitialiser);

        add(panneauGraphique, BorderLayout.CENTER);
        add(scrollPane, BorderLayout.SOUTH);
        add(panneauControle, BorderLayout.NORTH);

        timer = new Timer(500, e -> {
            mettreAJourSimulation();
            panneauGraphique.repaint();
        });
        timer.start();

        setLocationRelativeTo(null);
        setVisible(true);

        log("Simulation démarrée. Température cible: " + TEMPERATURE_CIBLE + " °C");
    }

    private void mettreAJourSimulation() {
        historiqueTemperature.add(temperatureCorporelle);

        if (historiqueTemperature.size() > 100) {
            historiqueTemperature.remove(0);
            if (!historiqueActions.isEmpty()) {
                historiqueActions.remove(0);
            }
        }

        double ecart = temperatureCorporelle - TEMPERATURE_CIBLE;

        if (ecart > 0.5) {
            tauxTranspiration = Math.min(tauxTranspiration + 0.2, 2.0);
            if (!historiqueActions.contains("Transpiration activée")) {
                historiqueActions.add("Transpiration activée");
                log("Transpiration activée pour refroidir le corps");
            }
        } else if (ecart < 0.2) {
            tauxTranspiration = Math.max(tauxTranspiration - 0.2, 0.0);
            if (tauxTranspiration < 0.1 && historiqueActions.contains("Transpiration activée")) {
                historiqueActions.add("Transpiration désactivée");
                log("Transpiration désactivée");
            }
        }

        if (ecart < -0.5) {
            tauxFrisson = Math.min(tauxFrisson + 0.2, 2.0);
            if (!historiqueActions.contains("Frissons activés")) {
                historiqueActions.add("Frissons activés");
                log("Frissons activés pour réchauffer le corps");
            }
        } else if (ecart > -0.2) {
            tauxFrisson = Math.max(tauxFrisson - 0.2, 0.0);
            if (tauxFrisson < 0.1 && historiqueActions.contains("Frissons activés")) {
                historiqueActions.add("Frissons désactivés");
                log("Frissons désactivés");
            }
        }

        if (ecart > 0.3 && !vasodilationActive) {
            vasodilationActive = true;
            vasoconstrictionActive = false;
            historiqueActions.add("Vasodilatation activée");
            log("Vasodilatation activée pour augmenter la perte de chaleur");
        } else if (ecart < -0.3 && !vasoconstrictionActive) {
            vasoconstrictionActive = true;
            vasodilationActive = false;
            historiqueActions.add("Vasoconstriction activée");
            log("Vasoconstriction activée pour réduire la perte de chaleur");
        } else if (Math.abs(ecart) < 0.2) {
            if (vasodilationActive) {
                vasodilationActive = false;
                historiqueActions.add("Vasodilatation désactivée");
                log("Vasodilatation désactivée");
            }
            if (vasoconstrictionActive) {
                vasoconstrictionActive = false;
                historiqueActions.add("Vasoconstriction désactivée");
                log("Vasoconstriction désactivée");
            }
        }

        double gainChaleurMetabolique = tauxMetabolisme + tauxFrisson;

        double perteChaleurEnvironnement = TAUX_TRANSFERT_CHALEUR *
            (temperatureCorporelle - TEMPERATURE_ENVIRONNEMENT);

        if (vasodilationActive) {
            perteChaleurEnvironnement *= 1.5;
        } else if (vasoconstrictionActive) {
            perteChaleurEnvironnement *= 0.5;
        }

        double perteChaleurTranspiration = tauxTranspiration;

        double changementTemperature =
            gainChaleurMetabolique - perteChaleurEnvironnement - perteChaleurTranspiration + perturbationExterne;

        temperatureCorporelle += changementTemperature * 0.1;

        if (historiqueTemperature.size() % 10 == 0) {
            log(String.format("T: %.2f°C, Écart: %.2f°C, Métabolisme: %.2f, Transpiration: %.2f, Frissons: %.2f",
                             temperatureCorporelle, ecart, tauxMetabolisme, tauxTranspiration, tauxFrisson));
        }
    }

    private void dessinerGraphique(Graphics g) {
        int largeur = panneauGraphique.getWidth();
        int hauteur = panneauGraphique.getHeight();

        g.setColor(Color.BLACK);
        g.drawLine(50, hauteur - 50, largeur - 50, hauteur - 50);
        g.drawLine(50, 50, 50, hauteur - 50);

        g.drawString("Temps", largeur / 2, hauteur - 20);
        g.drawString("Température (°C)", 10, hauteur / 2);

        for (int t = 35; t <= 39; t++) {
            int y = hauteur - 50 - (t - 35) * (hauteur - 100) / 4;
            g.drawLine(45, y, 55, y);
            g.drawString(Integer.toString(t), 20, y + 5);
        }

        int yCible = hauteur - 50 - (int)((TEMPERATURE_CIBLE - 35) * (hauteur - 100) / 4);
        g.setColor(Color.GREEN);
        g.drawLine(50, yCible, largeur - 50, yCible);
        g.drawString("Cible: " + TEMPERATURE_CIBLE + "°C", largeur - 150, yCible - 5);

        if (historiqueTemperature.size() > 1) {
            g.setColor(Color.RED);
            int xStep = (largeur - 100) / Math.max(1, historiqueTemperature.size() - 1);

            for (int i = 0; i < historiqueTemperature.size() - 1; i++) {
                int x1 = 50 + i * xStep;
                int y1 = hauteur - 50 - (int)((historiqueTemperature.get(i) - 35) * (hauteur - 100) / 4);
                int x2 = 50 + (i + 1) * xStep;
                int y2 = hauteur - 50 - (int)((historiqueTemperature.get(i + 1) - 35) * (hauteur - 100) / 4);
                g.drawLine(x1, y1, x2, y2);
            }
        }

        g.setColor(Color.BLUE);
        for (int i = 0; i < historiqueActions.size(); i++) {
            int index = historiqueTemperature.size() - historiqueActions.size() + i;
            if (index >= 0 && index < historiqueTemperature.size()) {
                int x = 50 + index * (largeur - 100) / Math.max(1, historiqueTemperature.size() - 1);
                int y = 70 + (i % 5) * 15;
                g.drawString(historiqueActions.get(i), x, y);
            }
        }

        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 14));
        g.drawString(String.format("Température actuelle: %.2f°C", temperatureCorporelle),
                    largeur - 250, 30);
    }

    private void log(String message) {
        zoneLog.append(message + "\n");
        zoneLog.setCaretPosition(zoneLog.getDocument().getLength());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RegulationTemperature::new);
    }
}
