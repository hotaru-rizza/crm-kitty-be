package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DayOfWeek {
    MONDAY("monday", 1),
    TUESDAY("tuesday", 2),
    WEDNESDAY("wednesday", 3),
    THURSDAY("thursday", 4),
    FRIDAY("friday", 5),
    SATURDAY("saturday", 6),
    SUNDAY("sunday", 7);

    private final String value;
    private final int order;

    public static DayOfWeek fromValue(String value) {
        for (DayOfWeek day : values()) {
            if (day.value.equalsIgnoreCase(value)) {
                return day;
            }
        }
        throw new IllegalArgumentException("Unknown day of week: " + value);
    }

    public static DayOfWeek fromJavaDayOfWeek(java.time.DayOfWeek javaDow) {
        return switch (javaDow) {
            case MONDAY -> MONDAY;
            case TUESDAY -> TUESDAY;
            case WEDNESDAY -> WEDNESDAY;
            case THURSDAY -> THURSDAY;
            case FRIDAY -> FRIDAY;
            case SATURDAY -> SATURDAY;
            case SUNDAY -> SUNDAY;
        };
    }

    public String getDisplayName() {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
