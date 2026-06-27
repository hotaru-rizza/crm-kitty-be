package com.inkflow.crm.module.appointment.dto;

import com.inkflow.crm.common.validation.ValidAppointmentTimeRange;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidAppointmentTimeRange
public class CreateAppointmentRequest {

    private UUID clientId;

    @NotNull(message = "Artist ID is required")
    private UUID artistId;

    /** Legacy single-service create; ignored when {@code items} is provided. */
    private UUID serviceId;

    @NotNull(message = "Location ID is required")
    private UUID locationId;

    private UUID projectId;

    @NotNull(message = "Start time is required")
    @Future(message = "Start time must be in the future")
    private Instant startTime;

    @NotNull(message = "End time is required")
    private Instant endTime;

    /** Legacy price; ignored when {@code items} is provided. */
    @DecimalMin(value = "0.0", message = "Price must be positive")
    private BigDecimal price;

    private List<AppointmentItemRequest> items;

    @DecimalMin(value = "0.0", message = "Prepayment must be positive")
    private BigDecimal prepayment;

    @DecimalMin(value = "0.0", message = "Discount must be positive")
    private BigDecimal discount;

    private String notes;
    private String sketchImage;

    /** When true, blocks time without client/services/payment lifecycle. */
    private Boolean reservation;
}
