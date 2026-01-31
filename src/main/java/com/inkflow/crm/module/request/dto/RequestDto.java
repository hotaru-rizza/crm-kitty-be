package com.inkflow.crm.module.request.dto;

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
public class RequestDto {
    private UUID id;
    private String source;
    private String clientName;
    private String clientNickname;
    private String message;
    private String phone;
    private String instagram;
    private String status;
    private UUID convertedClientId;
    private Instant createdAt;
    private Instant repliedAt;
    private Instant convertedAt;
}
