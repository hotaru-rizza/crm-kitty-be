package com.inkflow.crm.module.email.service;

import com.inkflow.crm.module.email.enums.TriggerType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class EmailDedupeKeys {

    static final int MAX_LENGTH = 128;

    private EmailDedupeKeys() {
    }

    static String forEnqueue(UUID tenantId, TriggerType triggerType, UUID entityId, UUID templateId) {
        if (entityId == null) {
            return null;
        }

        String raw = tenantId + ":" + triggerType.name() + ":" + entityId + ":" + templateId;
        return sha256Hex(raw);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
