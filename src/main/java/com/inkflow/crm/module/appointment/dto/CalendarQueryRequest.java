package com.inkflow.crm.module.appointment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarQueryRequest {

    @NotNull(message = "From date is required")
    private Instant from;

    @NotNull(message = "To date is required")
    private Instant to;

    private List<UUID> artistIds;
    private UUID locationId;
}
