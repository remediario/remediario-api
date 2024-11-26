package dev.feumpi;

import io.javalin.Javalin;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Base64;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {

        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath("/opt/homebrew/Cellar/tesseract-lang/4.1.0/share/tessdata/"); // Altere para o caminho correto
        tesseract.setLanguage("por"); // Define o idioma (por = português)

        var app = Javalin.create(config -> {
            config.http.maxRequestSize = 50_000_000; // 10 MB
        }).start(7070);

        app.get("/", ctx -> ctx.result("Hello World"));
        app.get("/remediario", ctx -> ctx.result("Remediario"));

        app.post("/ocr", ctx -> {
            try {
                // Recebe o Base64 do corpo da requisição
                String requestBody = ctx.body();
                System.out.println(requestBody);
                JSONObject json = new JSONObject(requestBody);

                // Extrai a string Base64 do JSON
                String base64Image = json.getString("image");

                // Remove o prefixo (se existir) e decodifica o Base64
                String base64Content = base64Image.replaceFirst("^data:image/[^;]+;base64,", "");
                byte[] imageBytes = Base64.getDecoder().decode(base64Content);

                // Converte os bytes em uma imagem BufferedImage
                ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
                BufferedImage image = ImageIO.read(inputStream);

                //Converter para grayscale
                BufferedImage grayImage = new BufferedImage(
                        image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
                Graphics g = grayImage.getGraphics();
                g.drawImage(image, 0, 0, null);
                g.dispose();

                //Converter para preto e branco
                BufferedImage binaryImage = new BufferedImage(
                        grayImage.getWidth(), grayImage.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
                Graphics2D g2d = binaryImage.createGraphics();
                g2d.drawImage(grayImage, 0, 0, null);
                g2d.dispose();

                //Aumentar o tamanho da imagem
                int newWidth = binaryImage.getWidth() * 2;
                int newHeight = binaryImage.getHeight() * 2;
                BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, image.getType());
                g2d = resizedImage.createGraphics();
                g2d.drawImage(binaryImage, 0, 0, newWidth, newHeight, null);
                g2d.dispose();

                BufferedImage finalImage = rotateImage(resizedImage, 90);

                //showImage(finalImage);

                // Realiza o OCR na imagem
                String text = tesseract.doOCR(finalImage);
                System.out.println(text);

                ArrayList<String> remedios = new ArrayList<String>();
                remedios.add("Venlafaxina");
                remedios.add("Tylenol");

                for(String remedio: remedios) {
                   if(text.contains(remedio)) {
                       System.out.println(remedio);
                        ctx.result(remedio).contentType("text/plain");
                        return;
                   }
                }

                // Retorna o texto como resposta
                ctx.result(text).contentType("text/plain");


            } catch (TesseractException e) {
                ctx.status(500).result("Erro ao realizar OCR: " + e.getMessage());
            } catch (Exception e) {
                ctx.status(400).result("Erro ao processar a imagem: " + e.getMessage());
            }
        });
    }

    public static void showImage(BufferedImage image) {
        // Create a JFrame to hold the image
        JFrame frame = new JFrame("Image Display");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Create a JPanel to render the image
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (image != null) {
                    // Draw the image in the center of the panel
                    g.drawImage(image, 0, 0, this.getWidth(), this.getHeight(), this);
                }
            }

            @Override
            public Dimension getPreferredSize() {
                // Set the preferred size to match the image dimensions
                return new Dimension(image.getWidth(), image.getHeight());
            }
        };

        // Add the panel to the frame
        frame.add(panel);
        frame.pack(); // Adjust the frame size to fit the panel
        frame.setLocationRelativeTo(null); // Center the frame on the screen
        frame.setVisible(true); // Make the frame visible
    }

    public static BufferedImage rotateImage(BufferedImage originalImage, double angle) {
        // Calculate the angle in radians
        double radians = Math.toRadians(angle);

        // Get the width and height of the original image
        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // Calculate the new dimensions for the rotated image
        int newWidth = (int) Math.round(Math.abs(width * Math.cos(radians)) + Math.abs(height * Math.sin(radians)));
        int newHeight = (int) Math.round(Math.abs(height * Math.cos(radians)) + Math.abs(width * Math.sin(radians)));

        // Create a new buffered image to hold the rotated image
        BufferedImage rotatedImage = new BufferedImage(newWidth, newHeight, originalImage.getType());

        // Create a graphics object to draw on the rotated image
        Graphics2D g2d = rotatedImage.createGraphics();

        // Set rendering hints for better quality
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Apply the rotation transformation
        AffineTransform transform = new AffineTransform();
        transform.translate(newWidth / 2.0, newHeight / 2.0); // Move to center of new canvas
        transform.rotate(radians);                           // Rotate by the specified angle
        transform.translate(-width / 2.0, -height / 2.0);    // Move back to the origin of the original image
        g2d.drawImage(originalImage, transform, null);

        g2d.dispose(); // Release resources
        return rotatedImage;
    }
}