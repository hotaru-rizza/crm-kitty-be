package com.inkflow.crm.domain.enums;

public enum LeaveStatus {
    PENDING("Очікує"),
    APPROVED("Підтверджено"),
    REJECTED("Відхилено");

    private final String displayName;

    LeaveStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
