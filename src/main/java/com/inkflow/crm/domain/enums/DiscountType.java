package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DiscountType {
    PERCENT("percent"),
    FIXED("fixed");

    private final String value;

    public static DiscountType fromValue(String value) {
        for (DiscountType type : values()) {
            if (type.value.equalsIgnoreCase(value)) return type;
        }
        throw new IllegalArgumentException("Unknown discount type: " + value);
    }
}
