package com.inkflow.crm.module.settings.dto;

import lombok.Data;

@Data
public class UpdateUserSettingsRequest {
    private String language;
    private String startPage;
}
