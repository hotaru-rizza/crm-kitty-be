package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuditAction {
    CREATE(AuditActionCategory.GENERIC),
    UPDATE(AuditActionCategory.GENERIC),
    DELETE(AuditActionCategory.GENERIC),
    CANCEL(AuditActionCategory.APPOINTMENT),
    CONFIRM(AuditActionCategory.APPOINTMENT),
    RESCHEDULE(AuditActionCategory.APPOINTMENT),
    STATUS_CHANGE(AuditActionCategory.REQUEST),
    CONVERT(AuditActionCategory.REQUEST),
    PAYMENT(AuditActionCategory.FINANCE),
    TXN_INCOME(AuditActionCategory.FINANCE),
    TXN_EXPENSE(AuditActionCategory.FINANCE),
    BLACKLIST_ADD(AuditActionCategory.CLIENT),
    BLACKLIST_REMOVE(AuditActionCategory.CLIENT),
    SCHEDULE_SET(AuditActionCategory.STAFF),
    STAFF_INVITE(AuditActionCategory.STAFF),
    STAFF_DEACTIVATE(AuditActionCategory.STAFF),
    PERMISSIONS_CHANGE(AuditActionCategory.ROLE),
    LOGIN(AuditActionCategory.AUTH),
    FAILED(AuditActionCategory.GENERIC);

    private final AuditActionCategory category;

    public String getValue() {
        return name();
    }

    public static AuditAction fromValue(String value) {
        for (AuditAction action : values()) {
            if (action.name().equalsIgnoreCase(value)) {
                return action;
            }
        }
        throw new IllegalArgumentException("Unknown audit action: " + value);
    }
}
