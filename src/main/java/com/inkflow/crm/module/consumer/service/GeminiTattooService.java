package com.inkflow.crm.module.consumer.service;

import com.inkflow.crm.config.GeminiProperties;
import com.inkflow.crm.integration.gemini.GeminiImageClient;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest;
import com.inkflow.crm.integration.gemini.dto.GeminiGenerateContentRequest.GeminiPart;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiTattooService {

    private final GeminiProperties geminiProperties;
    private final GeminiImageClient geminiImageClient;
    private final GeminiImagePreprocessor imagePreprocessor;

    public String generateTattooTryOn(
            String bodyImageSrc,
            String sketchImageSrc,
            double xNorm,
            double yNorm,
            double sizeNorm,
            double angleDeg
    ) throws Exception {
        String bodyBase64 = imagePreprocessor.toBase64Jpeg(bodyImageSrc);
        String sketchBase64 = imagePreprocessor.toBase64Jpeg(sketchImageSrc);
        String positionDescription = describePosition(xNorm, yNorm, sizeNorm, angleDeg);

        log.info("Try-on Gemini request: position={}", positionDescription);

        String prompt = buildTryOnPrompt(positionDescription);
        List<GeminiPart> parts = new ArrayList<>();
        parts.add(GeminiPart.text(prompt));
        parts.add(GeminiPart.jpegInline(bodyBase64));
        parts.add(GeminiPart.jpegInline(sketchBase64));

        GeminiGenerateContentRequest request = geminiImageClient.imageRequestWithParts(
                parts,
                geminiProperties.getTryOnTemperature()
        );

        return geminiImageClient.generateImage(request);
    }

    public String generateSketch(String prompt, String bodyPartContext) throws Exception {
        String fullPrompt = "Generate a tattoo sketch design. Style: black ink on white background, clean lines. "
                + "The design should be suitable for: " + bodyPartContext + ". "
                + "User request: " + prompt
                + "\n\nIMPORTANT: Output ONLY the tattoo design sketch, no text, no watermarks.";

        GeminiGenerateContentRequest request = geminiImageClient.imageRequest(
                fullPrompt,
                geminiProperties.getImageTemperature()
        );

        return geminiImageClient.generateImage(request);
    }

    private String buildTryOnPrompt(String positionDescription) {
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

        String sizeDesc = switch (sizeBucket(sizeNorm)) {
            case SMALL -> "small (about 8-10cm)";
            case MEDIUM -> "medium (about 12-15cm)";
            case LARGE -> "large (about 18-22cm)";
            case XL -> "very large (covering most of the visible area)";
        };

        String angleDesc = "";
        if (Math.abs(angleDeg) > 5) {
            angleDesc = String.format(", rotated approximately %.0f degrees %s",
                    Math.abs(angleDeg), angleDeg > 0 ? "clockwise" : "counter-clockwise");
        }

        return String.format(
                "Position the tattoo in the %s-%s area of the visible body/skin, %s in size%s.",
                vertical, horizontal, sizeDesc, angleDesc
        );
    }

    private SizeBucket sizeBucket(double sizeNorm) {
        if (sizeNorm < 0.15) return SizeBucket.SMALL;
        if (sizeNorm < 0.30) return SizeBucket.MEDIUM;
        if (sizeNorm < 0.50) return SizeBucket.LARGE;
        return SizeBucket.XL;
    }

    private enum SizeBucket { SMALL, MEDIUM, LARGE, XL }
}
