package com.inkflow.crm.module.email.dto;

import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLogDto {
    private UUID id;
    private String recipientEmail;
    private String recipientName;
    private String subject;
    private EmailType type;
    private EmailStatus status;
    private String errorMessage;
    private Instant sentAt;
}
