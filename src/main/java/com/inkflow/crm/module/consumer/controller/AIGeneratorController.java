package com.inkflow.crm.module.consumer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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
@RestController
@RequestMapping("/public/consumer/generate")
public class AIGeneratorController {

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-image:generateContent";

    @Value("${gemini.api.key}")
    private String apiKey;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public record GenerateRequest(
            String prompt,
            String style,
            String colorMode,
            String background,
            String ratio,
            String bodyImage
    ) {}

    public record GenerateResponse(List<String> images, String error) {
        public static GenerateResponse success(List<String> images) { return new GenerateResponse(images, null); }
        public static GenerateResponse failure(String msg) { return new GenerateResponse(List.of(), msg); }
    }

    @PostMapping
    public ResponseEntity<GenerateResponse> generate(@RequestBody GenerateRequest request) {
        try {
            String geminiPrompt = buildPrompt(request);
            log.info("AI Generate: style={}, color={}, bg={}, ratio={}",
                    request.style(), request.colorMode(), request.background(), request.ratio());

            Map<String, Object> apiRequest;
            if ("body".equals(request.background()) && request.bodyImage() != null && !request.bodyImage().isEmpty()) {
                String bodyBase64 = prepareImage(request.bodyImage());
                apiRequest = buildRequestWithImage(geminiPrompt, bodyBase64);
            } else {
                apiRequest = buildTextOnlyRequest(geminiPrompt);
            }

            String responseJson = restClient.post()
                    .uri(API_URL + "?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(apiRequest))
                    .retrieve()
                    .body(String.class);

            String imageDataUri = extractImageFromResponse(responseJson);
            return ResponseEntity.ok(GenerateResponse.success(List.of(imageDataUri)));

        } catch (Exception e) {
            log.error("AI generation failed", e);
            return ResponseEntity.internalServerError()
                    .body(GenerateResponse.failure(e.getMessage()));
        }
    }

    private String buildPrompt(GenerateRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("Generate a tattoo design. ");
        sb.append("Design: ").append(req.prompt()).append(". ");

        if (req.style() != null && !req.style().isEmpty()) {
            sb.append("Style: ").append(req.style()).append(" tattoo style. ");
        }

        if ("bw".equals(req.colorMode())) {
            sb.append("Use only black and gray ink, no color. ");
        } else {
            sb.append("Use full color, vibrant tattoo colors. ");
        }

        if ("body".equals(req.background())) {
            if (req.bodyImage() != null && !req.bodyImage().isEmpty()) {
                sb.append("Place the tattoo realistically on the body shown in the provided photo. ");
                sb.append("Make it look like a real, healed tattoo embedded in the skin. ");
                sb.append("The body photo must remain unchanged except for the added tattoo. ");
            } else {
                sb.append("Show the tattoo realistically placed on human skin/body. ");
                sb.append("Make it look like a real healed tattoo with skin texture visible through ink. ");
            }
        } else {
            sb.append("Draw the design on a clean white paper background, like a tattoo flash sheet. ");
            sb.append("No skin, no body — just the isolated tattoo artwork on white. ");
        }

        String aspectDesc = switch (req.ratio()) {
            case "9:16" -> "Portrait/vertical orientation.";
            case "16:9" -> "Landscape/horizontal orientation.";
            default -> "Square format.";
        };
        sb.append(aspectDesc);
        sb.append(" Output only the image, no text.");
        return sb.toString();
    }

    private Map<String, Object> buildTextOnlyRequest(String prompt) {
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "response_modalities", List.of("IMAGE", "TEXT"),
                        "temperature", 0.8
                )
        );
    }

    private Map<String, Object> buildRequestWithImage(String prompt, String imageBase64) {
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> imagePart = Map.of(
                "inline_data", Map.of("mime_type", "image/jpeg", "data", imageBase64)
        );
        return Map.of(
                "contents", List.of(Map.of("parts", List.of(textPart, imagePart))),
                "generationConfig", Map.of(
                        "response_modalities", List.of("IMAGE", "TEXT"),
                        "temperature", 0.8
                )
        );
    }

    private String extractImageFromResponse(String responseJson) throws Exception {
        JsonNode root = mapper.readTree(responseJson);
        if (root.has("error")) {
            throw new RuntimeException("Gemini API error: " + root.path("error").path("message").asText());
        }
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new RuntimeException("No candidates in Gemini response");
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        for (JsonNode part : parts) {
            JsonNode inlineData = part.has("inlineData") ? part.get("inlineData") : part.get("inline_data");
            if (inlineData != null) {
                String mimeType = inlineData.path("mimeType").asText(inlineData.path("mime_type").asText("image/png"));
                String data = inlineData.path("data").asText();
                return "data:" + mimeType + ";base64," + data;
            }
        }
        throw new RuntimeException("No image in Gemini response");
    }

    private String prepareImage(String src) throws Exception {
        byte[] bytes;
        if (src.startsWith("data:")) {
            int commaIdx = src.indexOf(',');
            bytes = Base64.getDecoder().decode(src.substring(commaIdx + 1));
        } else {
            bytes = URI.create(src).toURL().openStream().readAllBytes();
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IllegalArgumentException("Cannot decode image");

        int maxSide = 1024;
        int ow = img.getWidth(), oh = img.getHeight();
        if (ow > maxSide || oh > maxSide) {
            double scale = Math.min((double) maxSide / ow, (double) maxSide / oh);
            int nw = (int) (ow * scale), nh = (int) (oh * scale);
            BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
            g.dispose();
            img = resized;
        }

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
