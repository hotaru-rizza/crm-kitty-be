package com.inkflow.crm.module.request.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class RequestMessageDto {
    private UUID id;
    private String senderType;
    private String senderName;
    private String body;
    private String imageUrl;
    private Instant createdAt;
}
