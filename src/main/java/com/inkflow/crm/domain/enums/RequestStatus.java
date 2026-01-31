package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequestStatus {
    NEW("new", "New request"),
    REPLIED("replied", "Replied"),
    CONVERTED("converted", "Converted to client"),
    SPAM("spam", "Marked as spam");

    private final String value;
    private final String description;

    public static RequestStatus fromValue(String value) {
        for (RequestStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown request status: " + value);
    }
}
