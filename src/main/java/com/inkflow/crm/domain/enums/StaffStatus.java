package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StaffStatus {
    WORKING("working", "Currently working"),
    ON_VACATION("on_vacation", "On vacation"),
    SICK_LEAVE("sick_leave", "On sick leave"),
    FIRED("fired", "No longer employed");

    private final String value;
    private final String description;

    public static StaffStatus fromValue(String value) {
        for (StaffStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown staff status: " + value);
    }

    public boolean isAvailable() {
        return this == WORKING;
    }
}
