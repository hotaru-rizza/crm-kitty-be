package com.inkflow.crm.module.warehouse.dto;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WarehouseDto {
    private UUID id;
    private String name;
    private String notes;
    private UUID staffId;
    private String staffName;
    private Instant createdAt;
}
