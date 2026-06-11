package com.inkflow.crm.module.consumer.dto;

import java.time.Instant;
import java.util.UUID;

public record ConsumerBookingListItemDto(
        UUID id,
        String artistName,
        String status,
        String idea,
        String city,
        Instant createdAt
) {
}
