package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AppointmentStatus {
    SCHEDULED("scheduled", "Scheduled"),
    COMPLETED("completed", "Completed"),
    CANCELLED("cancelled", "Cancelled"),
    NO_SHOW("no_show", "No show");

    private final String value;
    private final String description;

    public static AppointmentStatus fromValue(String value) {
        for (AppointmentStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown appointment status: " + value);
    }

    public boolean isActive() {
        return this == SCHEDULED;
    }

    public boolean canTransitionTo(AppointmentStatus newStatus) {
        return switch (this) {
            case SCHEDULED -> newStatus == COMPLETED || newStatus == CANCELLED || newStatus == NO_SHOW;
            case COMPLETED, NO_SHOW, CANCELLED -> newStatus == SCHEDULED;
        };
    }
}
