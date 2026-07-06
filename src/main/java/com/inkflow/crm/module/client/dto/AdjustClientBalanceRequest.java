package com.inkflow.crm.module.client.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustClientBalanceRequest {

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @Size(max = 500)
    private String note;
}
