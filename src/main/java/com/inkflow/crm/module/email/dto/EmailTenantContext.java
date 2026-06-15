package com.inkflow.crm.module.email.dto;

import com.inkflow.crm.domain.enums.SupportedLocale;

public record EmailTenantContext(String studioName, String timezone, SupportedLocale locale) {
}
