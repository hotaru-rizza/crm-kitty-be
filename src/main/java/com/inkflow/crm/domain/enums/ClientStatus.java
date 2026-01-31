package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClientStatus {
    ACTIVE("active", "Active client"),
    INACTIVE("inactive", "Inactive client"),
    BLACKLISTED("blacklisted", "Blacklisted");

    private final String value;
    private final String description;

    public static ClientStatus fromValue(String value) {
        for (ClientStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown client status: " + value);
    }
}
