package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PricingType {
    FIXED("fixed", "Fixed price"),
    HOURLY("hourly", "Hourly rate"),
    PROJECT("project", "Project-based");

    private final String value;
    private final String description;

    public static PricingType fromValue(String value) {
        for (PricingType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown pricing type: " + value);
    }
}
