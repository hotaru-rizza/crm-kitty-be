package com.inkflow.crm.module.inventorycount.dto;

import lombok.*;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryCountItemDto {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productUnit;
    private Double expectedQty;
    private Double actualQty;
}
