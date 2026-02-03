package com.inkflow.crm.module.settings.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PermissionDto {
    private String value;
    private String category;
    private String description;
}
