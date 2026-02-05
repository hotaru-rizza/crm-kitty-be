package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentType {
    DEPOSIT("deposit", "Передоплата"),
    SERVICE_PAYMENT("service_payment", "Оплата послуги"),
    REFUND("refund", "Повернення коштів"),
    TIP("tip", "Чайові");

    private final String value;
    private final String description;

    public static PaymentType fromValue(String value) {
        for (PaymentType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown payment type: " + value);
    }

    public boolean isRefund() {
        return this == REFUND;
    }

    public boolean isIncome() {
        return this != REFUND;
    }
}
