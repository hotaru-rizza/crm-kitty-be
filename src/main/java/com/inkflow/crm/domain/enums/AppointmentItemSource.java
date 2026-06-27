package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppointmentItemSource {
    SERVICE("service"),
    CUSTOM("custom");

    private final String value;

    public static AppointmentItemSource fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Appointment item source is required");
        }
        for (AppointmentItemSource source : values()) {
            if (source.value.equalsIgnoreCase(value) || source.name().equalsIgnoreCase(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown appointment item source: " + value);
    }
}
