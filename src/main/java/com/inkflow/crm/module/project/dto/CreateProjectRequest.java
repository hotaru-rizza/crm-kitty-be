package com.inkflow.crm.module.project.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 2, max = 200, message = "Title must be between 2 and 200 characters")
    private String title;

    private String description;

    @NotNull(message = "Client ID is required")
    private UUID clientId;

    @NotNull(message = "Artist ID is required")
    private UUID artistId;

    @NotNull(message = "Estimated cost is required")
    @DecimalMin(value = "0.0", message = "Estimated cost must be positive")
    private BigDecimal estimatedCost;

    @NotNull(message = "Total sessions is required")
    @Min(value = 1, message = "Total sessions must be at least 1")
    private Integer totalSessions;
}
