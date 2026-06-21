package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ProjectStatus {
    IN_PROGRESS("in_progress", "Work in progress"),
    COMPLETED("completed", "Project completed"),
    ARCHIVED("archived", "Archived project");

    private final String value;
    private final String description;

    public static ProjectStatus fromValue(String value) {
        for (ProjectStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown project status: " + value);
    }

    public boolean isActive() {
        return this == IN_PROGRESS;
    }
}
