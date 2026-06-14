package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AccountType {
    SOLO("SOLO"),
    STUDIO("STUDIO");

    private final String value;

    public static AccountType fromValue(String value) {
        for (AccountType type : values()) {
            if (type.value.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown account type: " + value);
    }
}
