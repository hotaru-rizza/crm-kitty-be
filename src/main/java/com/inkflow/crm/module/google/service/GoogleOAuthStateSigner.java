package com.inkflow.crm.module.google.service;

import com.inkflow.crm.config.GoogleCalendarProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GoogleOAuthStateSigner {

    private static final long STATE_TTL_SECONDS = 600;

    private final GoogleCalendarProperties properties;

    public String sign(UUID staffId) {
        long expiresAt = Instant.now().getEpochSecond() + STATE_TTL_SECONDS;
        String payload = staffId + "." + expiresAt;
        String signature = hmac(payload);

        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "." + signature).getBytes(StandardCharsets.UTF_8));
    }

    public UUID verify(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("OAuth state is required");
        }

        String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
        String[] parts = decoded.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid OAuth state");
        }

        String payload = parts[0] + "." + parts[1];
        if (!hmac(payload).equals(parts[2])) {
            throw new IllegalArgumentException("Invalid OAuth state signature");
        }

        long expiresAt = Long.parseLong(parts[1]);
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new IllegalArgumentException("OAuth state expired");
        }

        return UUID.fromString(parts[0]);
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign OAuth state", e);
        }
    }

    private String stateSecret() {
        if (properties.getClientSecret() != null && !properties.getClientSecret().isBlank()) {
            return properties.getClientSecret();
        }
        throw new IllegalStateException("Google Calendar client secret is required for OAuth state signing");
    }
}
