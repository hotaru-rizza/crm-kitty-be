package com.inkflow.crm.module.invoice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SupplyInvoiceDto {
    private UUID id;
    private String name;
    private String supplierName;
    private String note;
    private String status;
    private UUID warehouseId;
    private String warehouseName;
    private List<SupplyInvoiceItemDto> items;
    private BigDecimal totalCost;
    private Instant createdAt;
}
