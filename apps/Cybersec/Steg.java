import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Steg {
    private static final byte[] MAGIC = "STEG1".getBytes(StandardCharsets.US_ASCII); // 5 bytes

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usageAndExit();
        }
        String cmd = args[0].toLowerCase();
        switch (cmd) {
            case "encode":
                if (args.length < 4) usageAndExit();
                String in = args[1];
                String out = args[2];
                // Le message est tout ce qui suit (pour autoriser des espaces)
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < args.length; i++) {
                    if (i > 3) sb.append(" ");
                    sb.append(args[i]);
                }
                String message = sb.toString();
                BufferedImage src = readImage(in);
                BufferedImage encoded = encode(src, message.getBytes(StandardCharsets.UTF_8));
                writeImage(encoded, out);
                System.out.println("OK: message caché dans " + out);
                break;
            case "decode":
                if (args.length != 2) usageAndExit();
                String input = args[1];
                BufferedImage img = readImage(input);
                String extracted = decode(img);
                System.out.println(extracted);
                break;
            default:
                usageAndExit();
        }
    }

    private static void usageAndExit() {
        System.err.println("Usage:");
        System.err.println("  java Steg encode <entree.png> <sortie.png> <message...>");
        System.err.println("  java Steg decode <image.png>");
        System.err.println("\nNotes:");
        System.err.println("  * Utiliser des PNG (ou autres formats sans perte).");
        System.err.println("  * La capacité ≈ largeur * hauteur * 3 bits.");
        System.exit(1);
    }

    private static BufferedImage readImage(String path) throws IOException {
        BufferedImage img = ImageIO.read(new File(path));
        if (img == null) throw new IOException("Impossible de lire l'image: " + path);
        // Assure un type ARGB pour getRGB/setRGB cohérents
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(img, 0, 0, null);
        return copy;
    }

    private static void writeImage(BufferedImage img, String path) throws IOException {
        String fmt = "png";
        String lower = path.toLowerCase();
        if (!lower.endsWith(".png")) {
            System.err.println("Attention: écriture en PNG forcée (ajoute .png si nécessaire).");
        }
        if (!ImageIO.write(img, fmt, new File(path))) {
            throw new IOException("Échec écriture PNG: " + path);
        }
    }

    private static BufferedImage encode(BufferedImage img, byte[] message) {
        // Payload = MAGIC (5) + length(4) + message
        byte[] lenBytes = intToBytes(message.length);
        byte[] payload = new byte[MAGIC.length + 4 + message.length];
        System.arraycopy(MAGIC, 0, payload, 0, MAGIC.length);
        System.arraycopy(lenBytes, 0, payload, MAGIC.length, 4);
        System.arraycopy(message, 0, payload, MAGIC.length + 4, message.length);

        long capacityBits = (long) img.getWidth() * img.getHeight() * 3L;
        long neededBits = (long) payload.length * 8L;
        if (neededBits > capacityBits) {
            throw new IllegalArgumentException("Message trop long. Capacité ~ " + capacityBits + " bits, requis " + neededBits + " bits.");
        }

        int bitIndex = 0;
        int totalBits = payload.length * 8;
        outer:
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = (argb) & 0xFF;

                // R, G, B : 1 bit chacun
                int[] channels = {r, g, b};
                for (int c = 0; c < 3; c++) {
                    if (bitIndex >= totalBits) {
                        // remonter l'ARGB et sortir
                        int packed = (a << 24) | (channels[0] << 16) | (channels[1] << 8) | channels[2];
                        img.setRGB(x, y, packed);
                        break outer;
                    }
                    int bit = ((payload[bitIndex / 8] >> (7 - (bitIndex % 8))) & 1);
                    channels[c] = (channels[c] & 0xFE) | bit;
                    bitIndex++;
                }
                int packed = (a << 24) | (channels[0] << 16) | (channels[1] << 8) | channels[2];
                img.setRGB(x, y, packed);
            }
        }
        return img;
    }

    private static String decode(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int countBytes = 0;
        int current = 0;
        int filled = 0;

        int expectedTotalBytes = MAGIC.length + 4; // on ne connaît pas encore la longueur du message
        Integer messageLen = null;

        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y);
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = (argb) & 0xFF;

                int[] channels = {r, g, b};
                for (int c = 0; c < 3; c++) {
                    int bit = channels[c] & 1;
                    current = (current << 1) | bit;
                    filled++;
                    if (filled == 8) {
                        out.write(current & 0xFF);
                        countBytes++;
                        filled = 0;
                        current = 0;

                        if (countBytes == MAGIC.length) {
                            byte[] buf = out.toByteArray();
                            for (int i = 0; i < MAGIC.length; i++) {
                                if (buf[i] != MAGIC[i]) {
                                    throw new IOException("Signature stéganographique absente ou incorrecte.");
                                }
                            }
                        } else if (countBytes == MAGIC.length + 4) {
                            byte[] buf = out.toByteArray();
                            messageLen = bytesToInt(buf, MAGIC.length); // décalage après MAGIC
                            if (messageLen < 0) throw new IOException("Longueur de message invalide.");
                            expectedTotalBytes = MAGIC.length + 4 + messageLen;
                        }

                        if (messageLen != null && countBytes == expectedTotalBytes) {
                            byte[] all = out.toByteArray();
                            byte[] msgBytes = new byte[messageLen];
                            System.arraycopy(all, MAGIC.length + 4, msgBytes, 0, messageLen);
                            return new String(msgBytes, StandardCharsets.UTF_8);
                        }
                    }
                }
            }
        }
        throw new IOException("Impossible d'extraire le message (capacité insuffisante ou image non encodée).");
    }

    private static byte[] intToBytes(int v) {
        return new byte[] {
                (byte)((v >>> 24) & 0xFF),
                (byte)((v >>> 16) & 0xFF),
                (byte)((v >>> 8) & 0xFF),
                (byte)(v & 0xFF)
        };
    }

    private static int bytesToInt(byte[] arr, int offset) {
        return ((arr[offset] & 0xFF) << 24) |
               ((arr[offset + 1] & 0xFF) << 16) |
               ((arr[offset + 2] & 0xFF) << 8) |
               (arr[offset + 3] & 0xFF);
    }
}
