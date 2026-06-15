package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


// TODO: What is in progress ? How it's being maintained?
@Getter
@RequiredArgsConstructor
public enum AppointmentStatus {
    NEW("new", "New appointment"),
    CONFIRMED("confirmed", "Confirmed by client"),
    IN_PROGRESS("in_progress", "Session in progress"),
    DONE("done", "Completed"),
    CANCELLED("cancelled", "Cancelled");

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
        return this == NEW || this == CONFIRMED || this == IN_PROGRESS;
    }

    public boolean canTransitionTo(AppointmentStatus newStatus) {
        return switch (this) {
            case NEW -> newStatus == CONFIRMED || newStatus == CANCELLED;
            case CONFIRMED -> newStatus == IN_PROGRESS || newStatus == CANCELLED;
            case IN_PROGRESS -> newStatus == DONE || newStatus == CANCELLED;
            case DONE, CANCELLED -> false;
        };
    }
}
