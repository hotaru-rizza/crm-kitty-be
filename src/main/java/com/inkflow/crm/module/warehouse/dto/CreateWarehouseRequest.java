package com.inkflow.crm.module.warehouse.dto;

import lombok.*;
import java.util.UUID;

@Data @NoArgsConstructor @AllArgsConstructor
public class CreateWarehouseRequest {
    private String name;
    private String notes;
    private UUID staffId;
}
