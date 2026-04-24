package com.inkflow.crm.module.invoice.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateSupplyInvoiceRequest {
    private String name;
    private String supplierName;
    private String note;
    private UUID warehouseId;
    private List<ItemRequest> items;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ItemRequest {
        private UUID productId;
        private Double quantity;
        private BigDecimal costPerUnit;
    }
}
