package com.inkflow.crm.module.email.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BuiltInTemplateKey {

    CONFIRMATION(TriggerType.BOOKING_CONFIRMED, null, TemplateCategory.CLIENT_OP, false),
    CANCELLATION(TriggerType.BOOKING_CANCELED, null, TemplateCategory.CLIENT_OP, false),
    RESCHEDULE(TriggerType.BOOKING_RESCHEDULED, null, TemplateCategory.CLIENT_OP, false),
    REMINDER(TriggerType.BEFORE_BOOKING, 24 * 60, TemplateCategory.CLIENT_OP, false),
    AFTERCARE(TriggerType.AFTER_BOOKING, 24 * 60, TemplateCategory.CLIENT_OP, false),
    REVIEW_REQUEST(TriggerType.AFTER_BOOKING, 48 * 60, TemplateCategory.CLIENT_OP, true),
    PREP_INSTRUCTIONS(TriggerType.BEFORE_BOOKING, 72 * 60, TemplateCategory.CLIENT_OP, true),
    BIRTHDAY(TriggerType.CLIENT_BIRTHDAY, null, TemplateCategory.MARKETING, false),
    WINBACK(TriggerType.CLIENT_INACTIVE, 30 * 24 * 60, TemplateCategory.MARKETING, false);

    private final TriggerType triggerType;
    private final Integer defaultOffsetMinutes;
    private final TemplateCategory category;
    private final boolean deletable;
}
