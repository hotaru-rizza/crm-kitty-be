package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PaymentMethod {
    CASH("cash", "Cash payment"),
    CARD("card", "Card payment"),
    SPLIT("split", "Split payment (cash + card)"),
    MONOBANK("monobank", "Monobank online payment"),
    BALANCE("balance", "Payment from client balance");

    public boolean isRealMoney() {
        return this != BALANCE;
    }

    private final String value;
    private final String description;

    public static PaymentMethod fromValue(String value) {
        for (PaymentMethod method : values()) {
            if (method.value.equalsIgnoreCase(value)) {
                return method;
            }
        }
        throw new IllegalArgumentException("Unknown payment method: " + value);
    }
}
