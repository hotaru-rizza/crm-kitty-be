package com.inkflow.crm.module.finance.dto;

import lombok.Data;

@Data
public class CategoryConfigUpsertRequest {
    private String label;
    private String color;
    private String plType;
}
