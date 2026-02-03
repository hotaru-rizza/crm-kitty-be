package com.inkflow.crm.domain.enums;

public enum LeaveType {
    VACATION("Відпустка"),
    SICK_LEAVE("Лікарняний"),
    PERSONAL("Особисті причини"),
    OTHER("Інше");

    private final String displayName;

    LeaveType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
