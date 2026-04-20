package cnn;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class Visualisation {

    /**
     * Visualise les activations d'une couche de convolution sous forme de grille d'images
     * en niveaux de gris, chaque carte d'activation étant normalisée entre 0 et 255.
     */
    public static BufferedImage visualiserActivations(double[][][] activations) {
        int profondeur = activations.length;
        int hauteur = activations[0].length;
        int largeur = activations[0][0].length;

        int colonnes = (int) Math.ceil(Math.sqrt(profondeur));
        int lignes = (int) Math.ceil((double) profondeur / colonnes);

        BufferedImage image = new BufferedImage(
            colonnes * largeur, lignes * hauteur, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        for (int f = 0; f < profondeur; f++) {
            int col = f % colonnes;
            int lig = f / colonnes;

            int x = col * largeur;
            int y = lig * hauteur;

            double min = Double.MAX_VALUE;
            double max = Double.MIN_VALUE;

            for (int h = 0; h < hauteur; h++) {
                for (int w = 0; w < largeur; w++) {
                    double val = activations[f][h][w];
                    min = Math.min(min, val);
                    max = Math.max(max, val);
                }
            }

            for (int h = 0; h < hauteur; h++) {
                for (int w = 0; w < largeur; w++) {
                    int valeur = 0;
                    if (max > min) {
                        valeur = (int) (255 * (activations[f][h][w] - min) / (max - min));
                    }

                    Color couleur = new Color(valeur, valeur, valeur);
                    image.setRGB(x + w, y + h, couleur.getRGB());
                }
            }
        }

        g.dispose();
        return image;
    }
}
