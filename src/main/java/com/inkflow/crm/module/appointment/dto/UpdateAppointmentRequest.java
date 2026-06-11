package com.inkflow.crm.module.appointment.dto;

import com.inkflow.crm.common.validation.ValidAppointmentTimeRange;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
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
@ValidAppointmentTimeRange
public class UpdateAppointmentRequest {

    private UUID artistId;
    private UUID serviceId;
    private UUID locationId;
    private UUID projectId;
    private Boolean clearProjectId;
    private Instant startTime;
    private Instant endTime;

    @DecimalMin(value = "0.0", message = "Price must be positive")
    private BigDecimal price;

    @DecimalMin(value = "0.0", message = "Prepayment must be positive")
    private BigDecimal prepayment;

    @DecimalMin(value = "0.0", message = "Discount must be positive")
    private BigDecimal discount;

    private String notes;
    private String sketchImage;

    @Pattern(regexp = "^(new|confirmed|in_progress|done|cancelled)$", message = "Invalid status")
    private String status;

    private String cancellationReason;
}
