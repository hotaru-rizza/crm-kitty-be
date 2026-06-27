package com.inkflow.crm.module.client.dto;

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
public class ClientBalanceDto {
    private UUID clientId;
    private BigDecimal balance;
    private List<ClientBalanceEntryDto> entries;
}
