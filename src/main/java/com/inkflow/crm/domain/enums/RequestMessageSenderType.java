package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RequestMessageSenderType {
    CLIENT("client"),
    STAFF("staff"),
    SYSTEM("system");

    private final String value;

    public static RequestMessageSenderType fromValue(String value) {
        for (RequestMessageSenderType senderType : values()) {
            if (senderType.value.equalsIgnoreCase(value) || senderType.name().equalsIgnoreCase(value)) {
                return senderType;
            }
        }
        throw new IllegalArgumentException("Unknown request message sender type: " + value);
    }
}
