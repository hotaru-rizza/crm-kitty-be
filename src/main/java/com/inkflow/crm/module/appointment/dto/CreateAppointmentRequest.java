package com.inkflow.crm.module.appointment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAppointmentRequest {

    @NotNull(message = "Client ID is required")
    private UUID clientId;

    @NotNull(message = "Artist ID is required")
    private UUID artistId;

    @NotNull(message = "Service ID is required")
    private UUID serviceId;

    @NotNull(message = "Location ID is required")
    private UUID locationId;

    private UUID projectId;

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    private Instant startTime;

    @NotNull(message = "End time is required")
    private Instant endTime;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be positive")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Prepayment must be positive")
    private BigDecimal prepayment;

    @DecimalMin(value = "0.0", message = "Discount must be positive")
    private BigDecimal discount;

    private String notes;
    private String sketchImage;
}
