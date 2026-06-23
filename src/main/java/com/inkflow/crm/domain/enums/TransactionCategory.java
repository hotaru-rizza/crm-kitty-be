package com.inkflow.crm.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TransactionCategory {
    SERVICE("service", "Service payment"),
    TIP("tip", "Tip"),
    RENT("rent", "Rent payment"),
    SUPPLIES("supplies", "Supplies purchase"),
    SALARY("salary", "Salary payment"),
    MARKETING("marketing", "Marketing"),
    EQUIPMENT("equipment", "Equipment"),
    MERCH("merch", "Merchandise"),
    OTHER("other", "Other");

    private final String value;
    private final String description;

    public static TransactionCategory fromValue(String value) {
        for (TransactionCategory category : values()) {
            if (category.value.equalsIgnoreCase(value)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown transaction category: " + value);
    }
}
