package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.module.consumer.dto.ProcessedImagesDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Base64;

@Slf4j
@Service
public class ImageProcessingService {

    private static final int MAX_SIDE = 1024;

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public ProcessedImagesDto prepareImages(
            String bodyImageSrc,
            String sketchImageSrc,
            double xNorm, double yNorm,
            double sizeNorm, double angleDeg
    ) throws Exception {
        BufferedImage bodyRaw = loadImage(bodyImageSrc);
        BufferedImage sketchRaw = loadImage(sketchImageSrc);

        BufferedImage body = resizeKeepAspect(bodyRaw, MAX_SIDE);
        int w = body.getWidth();
        int h = body.getHeight();

        int tattooSize = Math.max(1, (int) (sizeNorm * w));
        int tattooX = (int) (xNorm * w);
        int tattooY = (int) (yNorm * h);

        BufferedImage sketchResized = resizeTo(sketchRaw, tattooSize, tattooSize);
        BufferedImage sketchClean = removeBackground(sketchResized);

        BufferedImage composite = compositeMultiply(body, sketchClean, tattooX, tattooY, tattooSize, angleDeg);
        BufferedImage mask = generateMask(w, h, tattooX, tattooY, tattooSize, angleDeg);

        return new ProcessedImagesDto(
                toJpegDataUri(composite),
                toPngDataUri(mask),
                toJpegDataUri(body),
                w, h
        );
    }

    public String blendWithMask(String aiResultSrc, String originalBodyDataUri, String maskDataUri) throws Exception {
        BufferedImage aiResult = loadImage(aiResultSrc);
        BufferedImage originalBody = loadImage(originalBodyDataUri);
        BufferedImage mask = loadImage(maskDataUri);

        int w = originalBody.getWidth();
        int h = originalBody.getHeight();

        BufferedImage aiResized = resizeTo(aiResult, w, h);
        BufferedImage maskResized = resizeTo(mask, w, h);

        BufferedImage output = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int maskVal = maskResized.getRGB(x, y) & 0xFF;
                float alpha = maskVal / 255.0f;

                int origRgb = originalBody.getRGB(x, y);
                int aiRgb = aiResized.getRGB(x, y);

                int oR = (origRgb >> 16) & 0xFF;
                int oG = (origRgb >> 8) & 0xFF;
                int oB = origRgb & 0xFF;

                int aR = (aiRgb >> 16) & 0xFF;
                int aG = (aiRgb >> 8) & 0xFF;
                int aB = aiRgb & 0xFF;

                int fR = (int) (oR * (1 - alpha) + aR * alpha);
                int fG = (int) (oG * (1 - alpha) + aG * alpha);
                int fB = (int) (oB * (1 - alpha) + aB * alpha);

                output.setRGB(x, y, (fR << 16) | (fG << 8) | fB);
            }
        }

        return toJpegDataUri(output);
    }

    private BufferedImage loadImage(String src) throws Exception {
        if (src.startsWith("data:")) {
            int commaIdx = src.indexOf(',');
            byte[] bytes = Base64.getDecoder().decode(src.substring(commaIdx + 1));
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) throw new IllegalArgumentException("Cannot decode base64 image data");
            return img;
        }
        byte[] bytes = URI.create(src).toURL().openStream().readAllBytes();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IllegalArgumentException("Cannot decode image from URL: " + src);
        return img;
    }

    private BufferedImage resizeKeepAspect(BufferedImage src, int maxSide) {
        int ow = src.getWidth(), oh = src.getHeight();
        if (ow <= maxSide && oh <= maxSide) return src;
        double scale = Math.min((double) maxSide / ow, (double) maxSide / oh);
        int nw = (int) (ow * scale);
        int nh = (int) (oh * scale);
        return resizeTo(src, nw, nh);
    }

    private BufferedImage resizeTo(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private BufferedImage removeBackground(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                double brightness = (0.299 * r + 0.587 * g + 0.114 * b);

                if (brightness > 180) {
                    out.setRGB(x, y, 0x00FFFFFF);
                } else if (brightness > 140) {
                    int alpha = (int) (255 * (1.0 - (brightness - 140) / 40.0));
                    out.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
                } else {
                    out.setRGB(x, y, (0xFF << 24) | (r << 16) | (g << 8) | b);
                }
            }
        }
        return out;
    }

    private BufferedImage compositeMultiply(
            BufferedImage body, BufferedImage sketch,
            int x, int y, int size, double angleDeg
    ) {
        int w = body.getWidth(), h = body.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(body, 0, 0, null);
        g.dispose();

        BufferedImage sketchLayer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sg = sketchLayer.createGraphics();
        sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform at = new AffineTransform();
        at.translate(x + size / 2.0, y + size / 2.0);
        at.rotate(Math.toRadians(angleDeg));
        at.translate(-size / 2.0, -size / 2.0);
        sg.transform(at);
        sg.drawImage(sketch, 0, 0, size, size, null);
        sg.dispose();

        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int sRgba = sketchLayer.getRGB(px, py);
                int alpha = (sRgba >> 24) & 0xFF;
                if (alpha == 0) continue;

                int bodyRgb = out.getRGB(px, py);
                int bR = (bodyRgb >> 16) & 0xFF;
                int bG = (bodyRgb >> 8) & 0xFF;
                int bB = bodyRgb & 0xFF;

                int sR = (sRgba >> 16) & 0xFF;
                int sG = (sRgba >> 8) & 0xFF;
                int sB = sRgba & 0xFF;

                int rR = (bR * sR) / 255;
                int rG = (bG * sG) / 255;
                int rB = (bB * sB) / 255;

                float a = alpha / 255.0f;
                int fR = (int) (bR * (1 - a) + rR * a);
                int fG = (int) (bG * (1 - a) + rG * a);
                int fB = (int) (bB * (1 - a) + rB * a);

                out.setRGB(px, py, (fR << 16) | (fG << 8) | fB);
            }
        }

        return out;
    }

    private BufferedImage generateMask(
            int w, int h,
            int x, int y, int size, double angleDeg
    ) {
        BufferedImage mask = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = mask.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w, h);

        int padding = Math.max(4, size / 12);
        int padded = size + padding * 2;

        AffineTransform at = new AffineTransform();
        at.translate(x + size / 2.0, y + size / 2.0);
        at.rotate(Math.toRadians(angleDeg));
        at.translate(-padded / 2.0, -padded / 2.0);
        g.transform(at);

        g.setColor(Color.WHITE);
        g.fillRoundRect(0, 0, padded, padded, padding * 3, padding * 3);
        g.dispose();

        int smallW = Math.max(1, w / 4);
        int smallH = Math.max(1, h / 4);
        BufferedImage small = resizeTo(mask, smallW, smallH);
        return resizeTo(small, w, h);
    }

    private String toJpegDataUri(BufferedImage img) throws Exception {
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", baos);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private String toPngDataUri(BufferedImage img) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
