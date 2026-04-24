package com.inkflow.crm.module.inventorycount.dto;

import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InventoryCountDto {
    private UUID id;
    private String name;
    private String status;
    private UUID warehouseId;
    private String warehouseName;
    private List<InventoryCountItemDto> items;
    private Instant createdAt;
}
