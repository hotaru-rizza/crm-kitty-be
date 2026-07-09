package com.inkflow.crm.module.email.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TemplateVar {

    APP_NAME("app_name"),
    ACTION_URL("action_url"),
    DATETIME("datetime"),
    IP("ip"),
    DEVICE("device"),
    LOCATION("location"),

    STUDIO_NAME("studio_name"),
    USER_NAME("user_name"),
    INVITER_NAME("inviter_name"),
    ROLE("role"),

    CLIENT_NAME("client_name"),
    MASTER_NAME("master_name"),
    SERVICE("service"),
    DATE("date"),
    TIME("time"),
    ADDRESS("address"),
    LOCATION_NAME("location_name"),
    REMINDER_WINDOW("reminder_window"),
    APPOINTMENTS_COUNT("appointments_count"),
    SCHEDULE_LIST("schedule_list");

    private final String placeholder;
}
