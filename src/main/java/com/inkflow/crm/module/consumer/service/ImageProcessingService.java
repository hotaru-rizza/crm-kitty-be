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

/**
 * Prepares body + tattoo sketch images for AI try-on and blends model output back onto the original photo.
 */
@Slf4j
@Service
public class ImageProcessingService {

    private static final int MAX_BODY_SIDE_PX = 1024;

    /** Pixels brighter than this are treated as sketch paper background (fully transparent). */
    private static final double BACKGROUND_OPAQUE_THRESHOLD = 180;

    /** Fade paper background between this value and {@link #BACKGROUND_OPAQUE_THRESHOLD}. */
    private static final double BACKGROUND_FADE_START = 140;

    private static final double MASK_PADDING_FRACTION = 1.0 / 12.0;

    /** Downscale factor for mask softening (resize down then back up). */
    private static final int MASK_SOFTEN_DOWNSCALE_DIVISOR = 4;

    static {
        System.setProperty("java.awt.headless", "true");
    }

    /**
     * Builds composite preview, inpainting mask, and normalized body image for Gemini try-on.
     *
     * @param placementXNorm horizontal tattoo anchor (top-left), 0..1 relative to body width
     * @param placementYNorm vertical tattoo anchor (top-left), 0..1 relative to body height
     * @param sizeNorm       tattoo width as fraction of body width
     * @param rotationDeg    clockwise rotation in degrees
     */
    public ProcessedImagesDto prepareImages(
            String bodyImageSrc,
            String sketchImageSrc,
            double placementXNorm,
            double placementYNorm,
            double sizeNorm,
            double rotationDeg
    ) throws Exception {
        BufferedImage loadedBody = loadImage(bodyImageSrc);
        BufferedImage loadedSketch = loadImage(sketchImageSrc);

        BufferedImage normalizedBody = resizeKeepAspect(loadedBody, MAX_BODY_SIDE_PX);
        int bodyWidth = normalizedBody.getWidth();
        int bodyHeight = normalizedBody.getHeight();

        int stampSizePx = Math.max(1, (int) (sizeNorm * bodyWidth));
        int stampLeftPx = (int) (placementXNorm * bodyWidth);
        int stampTopPx = (int) (placementYNorm * bodyHeight);

        BufferedImage resizedSketch = resizeTo(loadedSketch, stampSizePx, stampSizePx);
        BufferedImage sketchWithoutPaper = removePaperBackground(resizedSketch);

        BufferedImage composite = compositeSketchOntoBody(
                normalizedBody, sketchWithoutPaper, stampLeftPx, stampTopPx, stampSizePx, rotationDeg);
        BufferedImage inpaintMask = buildInpaintMask(
                bodyWidth, bodyHeight, stampLeftPx, stampTopPx, stampSizePx, rotationDeg);

        return new ProcessedImagesDto(
                toJpegDataUri(composite),
                toPngDataUri(inpaintMask),
                toJpegDataUri(normalizedBody),
                bodyWidth,
                bodyHeight
        );
    }

    /** Alpha-blends AI output with the original body using a grayscale mask (white = AI region). */
    public String blendWithMask(String aiResultSrc, String originalBodyDataUri, String maskDataUri) throws Exception {
        BufferedImage aiResult = loadImage(aiResultSrc);
        BufferedImage originalBody = loadImage(originalBodyDataUri);
        BufferedImage mask = loadImage(maskDataUri);

        int width = originalBody.getWidth();
        int height = originalBody.getHeight();

        BufferedImage aiAligned = resizeTo(aiResult, width, height);
        BufferedImage maskAligned = resizeTo(mask, width, height);

        BufferedImage blended = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float aiWeight = channel(maskAligned.getRGB(x, y), ColorChannel.RED) / 255.0f;

                Rgb original = Rgb.fromRgb(originalBody.getRGB(x, y));
                Rgb generated = Rgb.fromRgb(aiAligned.getRGB(x, y));
                Rgb pixel = original.lerp(generated, aiWeight);

                blended.setRGB(x, y, pixel.toRgb());
            }
        }

        return toJpegDataUri(blended);
    }

    private BufferedImage loadImage(String src) throws Exception {
        if (src.startsWith("data:")) {
            int commaIndex = src.indexOf(',');
            byte[] bytes = Base64.getDecoder().decode(src.substring(commaIndex + 1));
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("Cannot decode base64 image data");
            }
            return image;
        }
        byte[] bytes = URI.create(src).toURL().openStream().readAllBytes();
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IllegalArgumentException("Cannot decode image from URL: " + src);
        }
        return image;
    }

    private BufferedImage resizeKeepAspect(BufferedImage source, int maxSidePx) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        if (sourceWidth <= maxSidePx && sourceHeight <= maxSidePx) {
            return source;
        }
        double scale = Math.min((double) maxSidePx / sourceWidth, (double) maxSidePx / sourceHeight);
        int targetWidth = (int) (sourceWidth * scale);
        int targetHeight = (int) (sourceHeight * scale);
        return resizeTo(source, targetWidth, targetHeight);
    }

    private BufferedImage resizeTo(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return resized;
    }

    /**
     * Makes light sketch paper transparent so only ink lines remain.
     * Uses luminance thresholds tuned for white/near-white scanner backgrounds.
     */
    private BufferedImage removePaperBackground(BufferedImage sketch) {
        int width = sketch.getWidth();
        int height = sketch.getHeight();
        BufferedImage withAlpha = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Rgb pixel = Rgb.fromRgb(sketch.getRGB(x, y));
                double luminance = pixel.luminance();

                if (luminance > BACKGROUND_OPAQUE_THRESHOLD) {
                    withAlpha.setRGB(x, y, 0x00FFFFFF);
                } else if (luminance > BACKGROUND_FADE_START) {
                    int alpha = (int) (255 * (1.0 - (luminance - BACKGROUND_FADE_START)
                            / (BACKGROUND_OPAQUE_THRESHOLD - BACKGROUND_FADE_START)));
                    withAlpha.setRGB(x, y, pixel.withAlpha(alpha));
                } else {
                    withAlpha.setRGB(x, y, pixel.withAlpha(255));
                }
            }
        }
        return withAlpha;
    }

    /**
     * Darkens body pixels under the sketch (multiply blend), then alpha-composites the sketch layer.
     */
    private BufferedImage compositeSketchOntoBody(
            BufferedImage body,
            BufferedImage sketch,
            int stampLeftPx,
            int stampTopPx,
            int stampSizePx,
            double rotationDeg
    ) {
        int width = body.getWidth();
        int height = body.getHeight();
        BufferedImage composite = copyToRgbCanvas(body);

        BufferedImage sketchLayer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sketchGraphics = sketchLayer.createGraphics();
        sketchGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        sketchGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        sketchGraphics.transform(stampTransform(stampLeftPx, stampTopPx, stampSizePx, rotationDeg, stampSizePx));
        sketchGraphics.drawImage(sketch, 0, 0, stampSizePx, stampSizePx, null);
        sketchGraphics.dispose();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sketchRgba = sketchLayer.getRGB(x, y);
                int alpha = channel(sketchRgba, ColorChannel.ALPHA);
                if (alpha == 0) {
                    continue;
                }

                Rgb bodyPixel = Rgb.fromRgb(composite.getRGB(x, y));
                Rgb sketchPixel = Rgb.fromRgb(sketchRgba);
                Rgb multiplied = bodyPixel.multiply(sketchPixel);
                float opacity = alpha / 255.0f;
                Rgb blended = bodyPixel.lerp(multiplied, opacity);

                composite.setRGB(x, y, blended.toRgb());
            }
        }

        return composite;
    }

    /**
     * White rounded rect on black — region Gemini should repaint. Soft edges via cheap downscale/upscale blur.
     */
    private BufferedImage buildInpaintMask(
            int width,
            int height,
            int stampLeftPx,
            int stampTopPx,
            int stampSizePx,
            double rotationDeg
    ) {
        BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = mask.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, width, height);

        int paddingPx = Math.max(4, (int) (stampSizePx * MASK_PADDING_FRACTION));
        int paddedSizePx = stampSizePx + paddingPx * 2;

        graphics.transform(stampTransform(stampLeftPx, stampTopPx, stampSizePx, rotationDeg, paddedSizePx));
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(0, 0, paddedSizePx, paddedSizePx, paddingPx * 3, paddingPx * 3);
        graphics.dispose();

        int smallWidth = Math.max(1, width / MASK_SOFTEN_DOWNSCALE_DIVISOR);
        int smallHeight = Math.max(1, height / MASK_SOFTEN_DOWNSCALE_DIVISOR);
        BufferedImage downscaled = resizeTo(mask, smallWidth, smallHeight);
        return resizeTo(downscaled, width, height);
    }

    private static AffineTransform stampTransform(
            int stampLeftPx,
            int stampTopPx,
            int stampSizePx,
            double rotationDeg,
            int drawSizePx
    ) {
        AffineTransform transform = new AffineTransform();
        transform.translate(stampLeftPx + stampSizePx / 2.0, stampTopPx + stampSizePx / 2.0);
        transform.rotate(Math.toRadians(rotationDeg));
        transform.translate(-drawSizePx / 2.0, -drawSizePx / 2.0);
        return transform;
    }

    private static BufferedImage copyToRgbCanvas(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private String toJpegDataUri(BufferedImage image) throws Exception {
        BufferedImage rgbCanvas = copyToRgbCanvas(image);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(rgbCanvas, "jpg", output);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private String toPngDataUri(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private static int channel(int packedColor, ColorChannel channel) {
        return switch (channel) {
            case RED -> (packedColor >> 16) & 0xFF;
            case GREEN -> (packedColor >> 8) & 0xFF;
            case BLUE -> packedColor & 0xFF;
            case ALPHA -> (packedColor >> 24) & 0xFF;
        };
    }

    private enum ColorChannel {
        RED, GREEN, BLUE, ALPHA
    }

    private record Rgb(int red, int green, int blue) {

        static Rgb fromRgb(int packedRgb) {
            return new Rgb(
                    (packedRgb >> 16) & 0xFF,
                    (packedRgb >> 8) & 0xFF,
                    packedRgb & 0xFF
            );
        }

        double luminance() {
            return 0.299 * red + 0.587 * green + 0.114 * blue;
        }

        Rgb multiply(Rgb other) {
            return new Rgb(
                    red * other.red / 255,
                    green * other.green / 255,
                    blue * other.blue / 255
            );
        }

        Rgb lerp(Rgb other, float weight) {
            float inverse = 1.0f - weight;
            return new Rgb(
                    (int) (red * inverse + other.red * weight),
                    (int) (green * inverse + other.green * weight),
                    (int) (blue * inverse + other.blue * weight)
            );
        }

        int toRgb() {
            return (red << 16) | (green << 8) | blue;
        }

        int withAlpha(int alpha) {
            return (alpha << 24) | toRgb();
        }
    }
}
