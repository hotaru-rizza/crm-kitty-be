package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequestSource {
    INSTAGRAM("instagram", "Instagram"),
    TELEGRAM("telegram", "Telegram"),
    WEBSITE("website", "Website"),
    REFERRAL("referral", "Referral"),
    WALK_IN("walk_in", "Walk-in"),
    APP("app", "Mobile App"),
    OTHER("other", "Other");

    private final String value;
    private final String description;

    public static RequestSource fromValue(String value) {
        for (RequestSource source : values()) {
            if (source.value.equalsIgnoreCase(value)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown request source: " + value);
    }
}
