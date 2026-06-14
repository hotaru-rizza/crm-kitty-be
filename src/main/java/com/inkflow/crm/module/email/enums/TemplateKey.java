package com.inkflow.crm.module.email.enums;

import lombok.Getter;

import java.util.Set;

import static com.inkflow.crm.module.email.enums.TemplateCategory.*;
import static com.inkflow.crm.module.email.enums.TemplateOwnership.*;
import static com.inkflow.crm.module.email.enums.TemplateVar.*;

@Getter
public enum TemplateKey {

    WELCOME_ONBOARD(LIFECYCLE, SYSTEM,
            Set.of(APP_NAME, STUDIO_NAME, USER_NAME, ACTION_URL)),

    TEAM_INVITE(LIFECYCLE, SYSTEM,
            Set.of(APP_NAME, STUDIO_NAME, INVITER_NAME, ROLE, ACTION_URL)),

    ROLE_CHANGED(LIFECYCLE, SYSTEM,
            Set.of(APP_NAME, STUDIO_NAME, USER_NAME, ROLE)),

    STAFF_DEACTIVATED(LIFECYCLE, SYSTEM,
            Set.of(APP_NAME, STUDIO_NAME, USER_NAME)),

    STAFF_REACTIVATED(LIFECYCLE, SYSTEM,
            Set.of(APP_NAME, STUDIO_NAME, USER_NAME)),

    TRIAL_STARTED(BILLING, SYSTEM,
            Set.of(APP_NAME, USER_NAME, STUDIO_NAME)),

    TRIAL_EXPIRING(BILLING, SYSTEM,
            Set.of(APP_NAME, USER_NAME, STUDIO_NAME)),

    BOOKING_REQUEST_RECEIVED(CLIENT_OP, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME, SERVICE, DATE, TIME)),

    BOOKING_CONFIRMED(CLIENT_OP, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE, DATE, TIME, ADDRESS)),

    BOOKING_REMINDER(CLIENT_OP, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, TIME, ADDRESS, REMINDER_WINDOW),
            24),

    PREP_INSTRUCTIONS(CLIENT_OP, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE, DATE, TIME)),

    BOOKING_RESCHEDULED(CLIENT_OP, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE, DATE, TIME, ADDRESS)),

    BOOKING_CANCELED(CLIENT_OP, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE, DATE, TIME)),

    AFTERCARE_INSTRUCTIONS(CLIENT_OP, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, SERVICE),
            24),

    REVIEW_REQUEST(CLIENT_OP, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME, MASTER_NAME, ACTION_URL)),

    NEW_APPOINTMENT(CLIENT_OP, SYSTEM,
            Set.of(APP_NAME, STUDIO_NAME, MASTER_NAME, CLIENT_NAME, SERVICE, DATE, TIME)),

    APPOINTMENT_CANCELED(CLIENT_OP, SYSTEM,
            Set.of(APP_NAME, STUDIO_NAME, MASTER_NAME, CLIENT_NAME, SERVICE, DATE, TIME)),

    APPOINTMENT_CHANGED(CLIENT_OP, SYSTEM,
            Set.of(APP_NAME, STUDIO_NAME, MASTER_NAME, CLIENT_NAME, SERVICE, DATE, TIME)),

    NEW_REQUEST_TO_APPROVE(CLIENT_OP, SYSTEM,
            Set.of(APP_NAME, STUDIO_NAME, USER_NAME, CLIENT_NAME, SERVICE, DATE, TIME)),

    BIRTHDAY(MARKETING, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME)),

    WINBACK(MARKETING, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME)),

    BULK_EMAIL(MARKETING, CONFIGURABLE,
            Set.of(APP_NAME, STUDIO_NAME, CLIENT_NAME));

    private final TemplateCategory category;
    private final TemplateOwnership ownership;
    private final Set<TemplateVar> availableVars;
    private final Integer defaultScheduleHours;

    TemplateKey(TemplateCategory category, TemplateOwnership ownership, Set<TemplateVar> availableVars) {
        this(category, ownership, availableVars, null);
    }

    TemplateKey(TemplateCategory category, TemplateOwnership ownership, Set<TemplateVar> availableVars,
                Integer defaultScheduleHours) {
        this.category = category;
        this.ownership = ownership;
        this.availableVars = availableVars;
        this.defaultScheduleHours = defaultScheduleHours;
    }

    public boolean isConfigurable() {
        return ownership == CONFIGURABLE;
    }

    public boolean isMarketing() {
        return category == MARKETING;
    }

    public boolean isScheduled() {
        return defaultScheduleHours != null;
    }

    public int getDefaultScheduleHours() {
        if (defaultScheduleHours == null) {
            throw new IllegalStateException("Template " + name() + " is not schedule-driven");
        }
        return defaultScheduleHours;
    }
}
