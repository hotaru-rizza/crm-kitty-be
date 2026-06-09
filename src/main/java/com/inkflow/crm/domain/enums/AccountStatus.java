package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountStatus {
    ACTIVE("active"),
    DEACTIVATED("deactivated");

    private final String value;

    public static AccountStatus fromValue(String value) {
        for (AccountStatus s : values()) {
            if (s.value.equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown account status: " + value);
    }
}
