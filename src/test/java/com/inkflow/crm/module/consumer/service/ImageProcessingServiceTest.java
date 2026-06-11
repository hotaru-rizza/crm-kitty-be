package com.inkflow.crm.module.consumer.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageProcessingServiceTest {

    private final ImageProcessingService imageProcessingService = new ImageProcessingService();

    @Test
    void shouldReturnCompositeMaskAndDimensionsWhenPrepareImages() throws Exception {
        String body = jpegDataUri(120, 160, Color.LIGHT_GRAY);
        String sketch = jpegDataUri(40, 40, Color.BLACK);

        var result = imageProcessingService.prepareImages(body, sketch, 0.3, 0.4, 0.2, 15.0);

        assertNotNull(result.compositeDataUri());
        assertNotNull(result.maskDataUri());
        assertNotNull(result.originalBodyDataUri());
        assertTrue(result.width() > 0);
        assertTrue(result.height() > 0);
        assertTrue(result.compositeDataUri().startsWith("data:image/jpeg;base64,"));
        assertTrue(result.maskDataUri().startsWith("data:image/png;base64,"));
    }

    @Test
    void shouldBlendAiResultWithOriginalUsingMask() throws Exception {
        String body = jpegDataUri(40, 40, Color.LIGHT_GRAY);
        String aiResult = jpegDataUri(40, 40, Color.RED);
        String mask = pngDataUri(40, 40, Color.WHITE);

        String blended = imageProcessingService.blendWithMask(aiResult, body, mask);

        assertNotNull(blended);
        assertTrue(blended.startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void shouldThrowWhenBase64ImageCannotBeDecoded() throws Exception {
        String sketch = jpegDataUri(10, 10, Color.BLACK);

        assertThrows(
                IllegalArgumentException.class,
                () -> imageProcessingService.prepareImages(
                        "data:image/jpeg;base64,not-valid", sketch, 0.5, 0.5, 0.2, 0.0)
        );
    }

    private String pngDataUri(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, color.getRGB() & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private String jpegDataUri(int width, int height, Color color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", baos);
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
