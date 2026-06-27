package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ClientBalanceReason {
    PAYMENT("payment"),
    CHARGE("charge"),
    BALANCE_SPEND("balance_spend"),
    REFUND("refund"),
    CHARGE_REVERSAL("charge_reversal"),
    MANUAL_ADJUSTMENT("manual_adjustment");

    private final String value;

    public static ClientBalanceReason fromValue(String value) {
        for (ClientBalanceReason reason : values()) {
            if (reason.value.equalsIgnoreCase(value)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("Unknown client balance reason: " + value);
    }
}
