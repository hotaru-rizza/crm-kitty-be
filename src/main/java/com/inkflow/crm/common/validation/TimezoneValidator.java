package com.inkflow.crm.common.validation;

import java.time.DateTimeException;
import java.time.ZoneId;

public final class TimezoneValidator {

    private TimezoneValidator() {}

    public static boolean isValid(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return false;
        }

        try {
            ZoneId.of(timezone);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }
}
