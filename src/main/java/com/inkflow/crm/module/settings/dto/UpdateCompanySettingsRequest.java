package com.inkflow.crm.module.settings.dto;

import lombok.Data;

@Data
public class UpdateCompanySettingsRequest {
    private String name;
    private String logoUrl;
    private String accountType;
}
