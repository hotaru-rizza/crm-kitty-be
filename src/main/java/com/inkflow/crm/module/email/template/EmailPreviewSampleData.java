package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.enums.TriggerType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class EmailPreviewSampleData {

    public static final String SAMPLE_CLIENT_NAME = "Олена";
    public static final String SAMPLE_STUDIO_NAME = "Black Ink Studio";
    public static final String SAMPLE_USER_NAME = "Максим";
    public static final String SAMPLE_INVITER_NAME = "Андрій";
    public static final String SAMPLE_ROLE = "Адміністратор";
    public static final String SAMPLE_MASTER_NAME = "Катерина";
    public static final String SAMPLE_SERVICE = "Тату сеанс";
    public static final String SAMPLE_DATE = "15 червня 2026";
    public static final String SAMPLE_TIME = "14:30";
    public static final String SAMPLE_ADDRESS = "вул. Хрещатик, 15, Київ";
    public static final String SAMPLE_DATETIME = "15.06.2026 о 14:30";
    public static final String SAMPLE_REMINDER_WINDOW = "за 24 години";
    public static final String SAMPLE_APPOINTMENTS_COUNT = "3";
    public static final String SAMPLE_SCHEDULE_LIST = """
            • 15.06 — 14:30, Катерина
            • 18.06 — 11:00, Олексій
            • 22.06 — 16:00, Катерина""";
    public static final String SAMPLE_ACTION_URL = "https://example.com/preview";
    public static final String SAMPLE_IP = "192.168.1.42";
    public static final String SAMPLE_DEVICE = "Chrome · macOS";
    public static final String SAMPLE_LOCATION = "Київ, Україна";

    private EmailPreviewSampleData() {
    }

    public static Map<String, String> forTrigger(TriggerType triggerType, String appName, String studioName) {
        return forVars(triggerType.getProvidedVars(), appName, studioName);
    }

    public static Map<String, String> forVars(Set<TemplateVar> variables, String appName, String studioName) {
        Map<String, String> result = new HashMap<>();

        for (TemplateVar variable : variables) {
            result.put(variable.getPlaceholder(), valueFor(variable));
        }

        result.put(TemplateVar.APP_NAME.getPlaceholder(), appName);
        result.put(
                TemplateVar.STUDIO_NAME.getPlaceholder(),
                hasText(studioName) ? studioName : SAMPLE_STUDIO_NAME
        );

        return result;
    }

    public static Map<String, String> forManualCompose(String recipientName, String studioName, String appName) {
        Map<String, String> result = new HashMap<>();
        result.put(
                TemplateVar.CLIENT_NAME.getPlaceholder(),
                hasText(recipientName) ? recipientName : SAMPLE_CLIENT_NAME
        );
        result.put(
                TemplateVar.STUDIO_NAME.getPlaceholder(),
                hasText(studioName) ? studioName : SAMPLE_STUDIO_NAME
        );
        result.put(TemplateVar.APP_NAME.getPlaceholder(), appName);
        // Preview macros that may be unused unless a location address is set.
        result.put(TemplateVar.ADDRESS.getPlaceholder(), SAMPLE_ADDRESS);
        result.put(TemplateVar.LOCATION_NAME.getPlaceholder(), "Black Ink · Поділ");
        return result;
    }

    private static String valueFor(TemplateVar variable) {
        return switch (variable) {
            case APP_NAME, STUDIO_NAME -> "";
            case ACTION_URL -> SAMPLE_ACTION_URL;
            case DATETIME -> SAMPLE_DATETIME;
            case IP -> SAMPLE_IP;
            case DEVICE -> SAMPLE_DEVICE;
            case LOCATION -> SAMPLE_LOCATION;
            case USER_NAME -> SAMPLE_USER_NAME;
            case INVITER_NAME -> SAMPLE_INVITER_NAME;
            case ROLE -> SAMPLE_ROLE;
            case CLIENT_NAME -> SAMPLE_CLIENT_NAME;
            case MASTER_NAME -> SAMPLE_MASTER_NAME;
            case SERVICE -> SAMPLE_SERVICE;
            case DATE -> SAMPLE_DATE;
            case TIME -> SAMPLE_TIME;
            case ADDRESS -> SAMPLE_ADDRESS;
            case LOCATION_NAME -> "Black Ink · Поділ";
            case REMINDER_WINDOW -> SAMPLE_REMINDER_WINDOW;
            case APPOINTMENTS_COUNT -> SAMPLE_APPOINTMENTS_COUNT;
            case SCHEDULE_LIST -> SAMPLE_SCHEDULE_LIST;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
