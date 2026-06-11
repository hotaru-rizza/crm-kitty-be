package com.inkflow.crm.module.email.dto;

public record PreparedEmail(
        String recipientEmail,
        String recipientName,
        String subject,
        String html) {
}
