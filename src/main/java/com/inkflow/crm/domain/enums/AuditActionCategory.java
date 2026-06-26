package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditActionCategory {
    GENERIC("generic"),
    APPOINTMENT("appointment"),
    REQUEST("request"),
    FINANCE("finance"),
    CLIENT("client"),
    STAFF("staff"),
    ROLE("role"),
    AUTH("auth");

    private final String value;

    public static AuditActionCategory fromValue(String value) {
        for (AuditActionCategory category : values()) {
            if (category.value.equalsIgnoreCase(value) || category.name().equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown audit action category: " + value);
    }
}
