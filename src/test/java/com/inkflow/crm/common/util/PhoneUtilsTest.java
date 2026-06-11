package com.inkflow.crm.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhoneUtilsTest {

    @Test
    void normalize_stripsFormatting() {
        assertEquals("+380991234567", PhoneUtils.normalize("+38 (099) 123-45-67"));
    }

    @Test
    void normalize_keepsPlusAndDigits() {
        assertEquals("+1234567890", PhoneUtils.normalize("+1 234 567 890"));
    }
}
