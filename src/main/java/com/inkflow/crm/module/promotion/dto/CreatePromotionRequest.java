package com.inkflow.crm.module.promotion.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreatePromotionRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    private String description;

    @NotBlank
    @Pattern(regexp = "^(percent|fixed)$")
    private String discountType;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal discountValue;

    private LocalDate validFrom;
    private LocalDate validTo;
    private List<UUID> serviceIds;
}
