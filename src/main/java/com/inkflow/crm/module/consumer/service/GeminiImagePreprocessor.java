package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.config.GeminiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class GeminiImagePreprocessor {

    private final GeminiProperties geminiProperties;

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public String toBase64Jpeg(String src) throws Exception {
        BufferedImage image = loadImage(src);
        return toBase64Jpeg(resizeIfNeeded(image));
    }

    public String toBase64Jpeg(BufferedImage image) throws Exception {
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", output);
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private BufferedImage loadImage(String src) throws Exception {
        byte[] bytes = readBytes(src);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IllegalArgumentException("Cannot decode image");
        }
        return image;
    }

    private byte[] readBytes(String src) throws Exception {
        if (src.startsWith("data:")) {
            int commaIndex = src.indexOf(',');
            return Base64.getDecoder().decode(src.substring(commaIndex + 1));
        }
        return URI.create(src).toURL().openStream().readAllBytes();
    }

    private BufferedImage resizeIfNeeded(BufferedImage image) {
        int maxSide = geminiProperties.getMaxImageSide();
        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= maxSide && height <= maxSide) {
            return image;
        }

        double scale = Math.min((double) maxSide / width, (double) maxSide / height);
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(image, 0, 0, newWidth, newHeight, null);
        graphics.dispose();
        return resized;
    }
}
