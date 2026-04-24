package com.inkflow.crm.module.invoice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SupplyInvoiceItemDto {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productUnit;
    private Double quantity;
    private BigDecimal costPerUnit;
}
