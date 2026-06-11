package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.config.GeminiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeminiImagePreprocessorTest {

    @Mock
    private GeminiProperties geminiProperties;

    @InjectMocks
    private GeminiImagePreprocessor preprocessor;

    @Test
    void shouldEncodeBufferedImageAsBase64Jpeg() throws Exception {
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 100, 50);
        graphics.dispose();

        String base64 = preprocessor.toBase64Jpeg(image);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        assertNotNull(decoded);
        assertEquals(100, decoded.getWidth());
        assertEquals(50, decoded.getHeight());
        assertFalse(base64.isEmpty());
    }

    @Test
    void shouldResizeImageWhenExceedsMaxSide() throws Exception {
        when(geminiProperties.getMaxImageSide()).thenReturn(512);

        String dataUri = toDataUri(createFilledImage(2048, 1024, Color.BLUE));
        String base64 = preprocessor.toBase64Jpeg(dataUri);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        assertEquals(512, decoded.getWidth());
        assertEquals(256, decoded.getHeight());
    }

    @Test
    void shouldNotResizeWhenWithinMaxSide() throws Exception {
        when(geminiProperties.getMaxImageSide()).thenReturn(1024);

        String dataUri = toDataUri(createFilledImage(800, 600, Color.GREEN));
        String base64 = preprocessor.toBase64Jpeg(dataUri);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        assertEquals(800, decoded.getWidth());
        assertEquals(600, decoded.getHeight());
    }

    @Test
    void shouldDecodeDataUriImageSource() throws Exception {
        when(geminiProperties.getMaxImageSide()).thenReturn(1024);

        String dataUri = toDataUri(createFilledImage(64, 64, Color.ORANGE));

        String base64 = preprocessor.toBase64Jpeg(dataUri);

        assertTrue(Base64.getDecoder().decode(base64).length > 0);
    }

    @Test
    void shouldThrowWhenImageDataIsInvalid() {
        String dataUri = "data:image/png;base64,!!!";

        assertThrows(IllegalArgumentException.class, () -> preprocessor.toBase64Jpeg(dataUri));
    }

    private static BufferedImage createFilledImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static String toDataUri(BufferedImage image) throws Exception {
        ByteArrayOutputStream pngOutput = new ByteArrayOutputStream();
        ImageIO.write(image, "png", pngOutput);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngOutput.toByteArray());
    }
}
