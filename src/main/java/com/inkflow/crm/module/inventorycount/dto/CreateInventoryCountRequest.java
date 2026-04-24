package com.inkflow.crm.module.inventorycount.dto;

import lombok.*;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateInventoryCountRequest {
    private String name;
    private UUID warehouseId;
}
