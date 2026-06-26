package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditEntityType {
    APPOINTMENT,
    CLIENT,
    TRANSACTION,
    REQUEST,
    STAFF,
    ROLE,
    SCHEDULE,
    LOCATION;

    public String getValue() {
        return name();
    }

    public static AuditEntityType fromValue(String value) {
        for (AuditEntityType entityType : values()) {
            if (entityType.name().equalsIgnoreCase(value)) {
                return entityType;
            }
        }
        throw new IllegalArgumentException("Unknown audit entity type: " + value);
    }
}
