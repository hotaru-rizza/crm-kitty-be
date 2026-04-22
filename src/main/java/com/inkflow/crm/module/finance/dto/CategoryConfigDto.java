package com.inkflow.crm.module.finance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryConfigDto {
    private UUID id;
    private String categoryKey;
    private String label;
    private String color;
    private String plType;
    private Boolean isActive;
    private Boolean isDefault;
}
