package com.inkflow.crm.module.consumer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.*;
import java.util.List;

@Slf4j
@Service
public class GeminiTattooService {

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent";
    private static final int MAX_BODY_SIDE = 1024;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public String generateTattooTryOn(
            String bodyImageSrc,
            String sketchImageSrc,
            double xNorm, double yNorm,
            double sizeNorm, double angleDeg
    ) throws Exception {
        String bodyBase64 = prepareBodyImage(bodyImageSrc);
        String sketchBase64 = prepareSketchImage(sketchImageSrc);
        String positionDescription = describePosition(xNorm, yNorm, sizeNorm, angleDeg);

        String prompt = buildPrompt(positionDescription);
        log.info("Sending to Gemini: position={}", positionDescription);

        Map<String, Object> request = buildRequest(prompt, bodyBase64, sketchBase64);
        String requestJson = mapper.writeValueAsString(request);

        String responseJson = restClient.post()
                .uri(API_URL + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .body(String.class);

        return extractImageFromResponse(responseJson);
    }

    public String generateSketch(String prompt, String bodyPartContext) throws Exception {
        String fullPrompt = "Generate a tattoo sketch design. Style: black ink on white background, clean lines. "
                + "The design should be suitable for: " + bodyPartContext + ". "
                + "User request: " + prompt
                + "\n\nIMPORTANT: Output ONLY the tattoo design sketch, no text, no watermarks.";

        Map<String, Object> textPart = Map.of("text", fullPrompt);
        Map<String, Object> content = Map.of("parts", List.of(textPart));
        Map<String, Object> generationConfig = Map.of(
                "response_modalities", List.of("IMAGE", "TEXT"),
                "temperature", 0.8
        );
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(content),
                "generationConfig", generationConfig
        );

        String requestJson = mapper.writeValueAsString(requestBody);

        String responseJson = restClient.post()
                .uri(API_URL + "?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestJson)
                .retrieve()
                .body(String.class);

        return extractImageFromResponse(responseJson);
    }

    private String buildPrompt(String positionDescription) {
        return "You are a professional tattoo visualization tool. "
                + "Take the tattoo design from the second image and realistically place it on the body in the first image. "
                + "\n\nPlacement instructions: " + positionDescription
                + "\n\nIMPORTANT RULES:"
                + "\n- The tattoo must look like a REAL, HEALED tattoo embedded in the skin"
                + "\n- Preserve the EXACT design, lines, and colors from the tattoo reference — do NOT simplify or change the artwork"
                + "\n- Natural skin texture must be slightly visible through the tattoo ink"
                + "\n- Match the lighting and shadows of the original body photo"
                + "\n- The body, pose, clothing, background must remain COMPLETELY unchanged"
                + "\n- The tattoo should follow the natural contours of the body"
                + "\n- Output only the final image with the tattoo applied, no text";
    }

    private String describePosition(double xNorm, double yNorm, double sizeNorm, double angleDeg) {
        String vertical = yNorm < 0.33 ? "upper" : yNorm < 0.66 ? "middle" : "lower";
        String horizontal = xNorm < 0.33 ? "left" : xNorm < 0.66 ? "center" : "right";

        String sizeDesc;
        if (sizeNorm < 0.15) sizeDesc = "small (about 8-10cm)";
        else if (sizeNorm < 0.30) sizeDesc = "medium (about 12-15cm)";
        else if (sizeNorm < 0.50) sizeDesc = "large (about 18-22cm)";
        else sizeDesc = "very large (covering most of the visible area)";

        String angleDesc = "";
        if (Math.abs(angleDeg) > 5) {
            angleDesc = String.format(", rotated approximately %.0f degrees %s",
                    Math.abs(angleDeg), angleDeg > 0 ? "clockwise" : "counter-clockwise");
        }

        return String.format("Position the tattoo in the %s-%s area of the visible body/skin, %s in size%s.",
                vertical, horizontal, sizeDesc, angleDesc);
    }

    private Map<String, Object> buildRequest(String prompt, String bodyBase64, String sketchBase64) {
        Map<String, Object> bodyImage = Map.of(
                "inline_data", Map.of("mime_type", "image/jpeg", "data", bodyBase64)
        );
        Map<String, Object> sketchImage = Map.of(
                "inline_data", Map.of("mime_type", "image/jpeg", "data", sketchBase64)
        );
        Map<String, Object> textPart = Map.of("text", prompt);

        Map<String, Object> content = Map.of(
                "parts", List.of(textPart, bodyImage, sketchImage)
        );

        Map<String, Object> generationConfig = Map.of(
                "response_modalities", List.of("IMAGE", "TEXT"),
                "temperature", 0.4
        );

        return Map.of(
                "contents", List.of(content),
                "generationConfig", generationConfig
        );
    }

    private String extractImageFromResponse(String responseJson) throws Exception {
        JsonNode root = mapper.readTree(responseJson);

        if (root.has("error")) {
            String errorMsg = root.path("error").path("message").asText();
            throw new RuntimeException("Gemini API error: " + errorMsg);
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new RuntimeException("No candidates in Gemini response");
        }

        JsonNode parts = candidates.get(0).path("content").path("parts");
        for (JsonNode part : parts) {
            if (part.has("inlineData") || part.has("inline_data")) {
                JsonNode inlineData = part.has("inlineData") ? part.get("inlineData") : part.get("inline_data");
                String mimeType = inlineData.path("mimeType").asText(
                        inlineData.path("mime_type").asText("image/png"));
                String data = inlineData.path("data").asText();
                return "data:" + mimeType + ";base64," + data;
            }
        }

        throw new RuntimeException("No image found in Gemini response");
    }

    private String prepareBodyImage(String src) throws Exception {
        BufferedImage img = loadImage(src);
        int ow = img.getWidth(), oh = img.getHeight();
        if (ow > MAX_BODY_SIDE || oh > MAX_BODY_SIDE) {
            double scale = Math.min((double) MAX_BODY_SIDE / ow, (double) MAX_BODY_SIDE / oh);
            int nw = (int) (ow * scale), nh = (int) (oh * scale);
            BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            img = resized;
        }
        return toBase64Jpeg(img);
    }

    private String prepareSketchImage(String src) throws Exception {
        BufferedImage img = loadImage(src);
        return toBase64Jpeg(img);
    }

    private BufferedImage loadImage(String src) throws Exception {
        byte[] bytes;
        if (src.startsWith("data:")) {
            int commaIdx = src.indexOf(',');
            bytes = Base64.getDecoder().decode(src.substring(commaIdx + 1));
        } else {
            bytes = URI.create(src).toURL().openStream().readAllBytes();
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IllegalArgumentException("Cannot decode image");
        return img;
    }

    private String toBase64Jpeg(BufferedImage img) throws Exception {
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
