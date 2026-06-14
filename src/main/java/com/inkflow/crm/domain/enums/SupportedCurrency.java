package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SupportedCurrency {
    UAH("UAH");

    private final String code;

    public static SupportedCurrency fromCode(String code) {
        for (SupportedCurrency currency : values()) {
            if (currency.code.equalsIgnoreCase(code)) {
                return currency;
            }
        }
        throw new IllegalArgumentException("Unsupported currency: " + code);
    }
}
