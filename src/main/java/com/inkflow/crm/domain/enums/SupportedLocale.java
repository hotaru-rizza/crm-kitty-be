package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SupportedLocale {
    UK("uk"),
    EN("en");

    private final String code;

    public static SupportedLocale fromCode(String code) {
        for (SupportedLocale locale : values()) {
            if (locale.code.equals(code)) {
                return locale;
            }
        }
        throw new IllegalArgumentException("Unsupported locale code: " + code);
    }
}
