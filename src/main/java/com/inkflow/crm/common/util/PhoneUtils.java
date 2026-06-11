package com.inkflow.crm.common.util;

public final class PhoneUtils {

    private PhoneUtils() {
    }

    public static String normalize(String phone) {
        return phone.replaceAll("[^0-9+]", "");
    }
}
