package com.inkflow.crm.module.email.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

import static com.inkflow.crm.module.email.enums.TemplateCategory.CLIENT_OP;
import static com.inkflow.crm.module.email.enums.TemplateCategory.MARKETING;
import static com.inkflow.crm.module.email.enums.TemplateVar.*;

@Getter
public enum TriggerType {

    BOOKING_CONFIRMED(CLIENT_OP, false, false, EnumSet.of(
            APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE, DATE, TIME, ADDRESS)),

    BOOKING_CANCELED(CLIENT_OP, false, false, EnumSet.of(
            APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE, DATE, TIME)),

    BOOKING_RESCHEDULED(CLIENT_OP, false, false, EnumSet.of(
            APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE, DATE, TIME, ADDRESS)),

    BOOKING_COMPLETED(CLIENT_OP, false, false, EnumSet.of(
            APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE, DATE, TIME)),

    BEFORE_BOOKING(CLIENT_OP, true, true, EnumSet.of(
            APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, TIME, ADDRESS, REMINDER_WINDOW)),

    AFTER_BOOKING(CLIENT_OP, true, true, EnumSet.of(
            APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE)),

    CLIENT_BIRTHDAY(MARKETING, true, false, EnumSet.of(
            APP_NAME, STUDIO_NAME, CLIENT_NAME)),

    CLIENT_INACTIVE(MARKETING, true, true, EnumSet.of(
            APP_NAME, STUDIO_NAME, CLIENT_NAME)),

    MANUAL(MARKETING, false, false, EnumSet.of(
            APP_NAME, STUDIO_NAME, CLIENT_NAME)),

    STAFF_APPOINTMENT(CLIENT_OP, false, false, EnumSet.of(
            APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE, DATE, TIME, ADDRESS));

    private final TemplateCategory category;
    private final boolean scheduled;
    private final boolean requiresOffset;
    private final Set<TemplateVar> providedVars;

    TriggerType(TemplateCategory category, boolean scheduled, boolean requiresOffset, Set<TemplateVar> providedVars) {
        this.category = category;
        this.scheduled = scheduled;
        this.requiresOffset = requiresOffset;
        this.providedVars = providedVars;
    }

    public boolean isEventDriven() {
        return !scheduled && this != MANUAL && this != STAFF_APPOINTMENT;
    }

    public boolean isClientAppointmentNotification() {
        return switch (this) {
            case BOOKING_CONFIRMED,
                 BOOKING_CANCELED,
                 BOOKING_RESCHEDULED,
                 BOOKING_COMPLETED,
                 BEFORE_BOOKING,
                 AFTER_BOOKING -> true;
            default -> false;
        };
    }

    public boolean isMailingTemplateTrigger() {
        return this != STAFF_APPOINTMENT;
    }
}
