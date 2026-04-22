package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SalaryType {
    NONE("none"),
    FIXED("fixed"),
    PERCENT("percent");

    private final String value;

    public static SalaryType fromValue(String value) {
        if (value == null) return NONE;
        for (SalaryType t : values()) {
            if (t.value.equalsIgnoreCase(value)) return t;
        }
        return NONE;
    }
}
