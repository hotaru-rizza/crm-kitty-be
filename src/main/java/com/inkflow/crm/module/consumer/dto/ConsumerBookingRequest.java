package com.inkflow.crm.module.consumer.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ConsumerBookingRequest(
        @NotNull UUID artistId,
        String clientName,
        String timing,
        String size,
        List<String> bodyZones,
        Boolean isCoverUp,
        String idea,
        List<String> references,
        String city,
        String contactMethod,
        String contactValue,
        String phone,
        String instagram
) {
}
