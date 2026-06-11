package com.inkflow.crm.module.consumer.dto;

import java.util.UUID;

public record ConsumerBookingResultDto(UUID id, String status, String artistName) {
}
