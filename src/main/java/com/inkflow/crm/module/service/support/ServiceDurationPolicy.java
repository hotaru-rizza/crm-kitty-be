package com.inkflow.crm.module.service.support;

import com.inkflow.crm.domain.enums.PricingType;

public final class ServiceDurationPolicy {

    public static final int DEFAULT_SLOT_MINUTES = 60;
    public static final int MIN_DURATION_MINUTES = 15;

    private ServiceDurationPolicy() {
    }

    public static int resolveForCreate(PricingType pricingType, Integer duration) {
        if (pricingType == PricingType.FIXED) {
            return duration;
        }
        if (duration != null && duration >= MIN_DURATION_MINUTES) {
            return duration;
        }
        return DEFAULT_SLOT_MINUTES;
    }

    public static Integer resolveForUpdate(
            PricingType pricingType,
            Integer requestedDuration,
            Integer currentDuration) {
        if (requestedDuration != null) {
            if (requestedDuration >= MIN_DURATION_MINUTES) {
                return requestedDuration;
            }
            if (pricingType == PricingType.FIXED) {
                return requestedDuration;
            }
            return DEFAULT_SLOT_MINUTES;
        }
        if (pricingType == PricingType.FIXED && currentDuration != null && currentDuration >= MIN_DURATION_MINUTES) {
            return currentDuration;
        }
        if (pricingType != PricingType.FIXED && (currentDuration == null || currentDuration < MIN_DURATION_MINUTES)) {
            return DEFAULT_SLOT_MINUTES;
        }
        return currentDuration;
    }

    public static boolean isDurationValidForCreate(String pricingType, Integer duration) {
        if (pricingType == null) {
            return true;
        }
        if (PricingType.FIXED.getValue().equalsIgnoreCase(pricingType)) {
            return duration != null && duration >= MIN_DURATION_MINUTES;
        }
        return duration == null || duration == 0 || duration >= MIN_DURATION_MINUTES;
    }

    public static boolean isDurationValidForUpdate(String pricingType, Integer duration) {
        if (duration == null) {
            return true;
        }
        if (duration == 0) {
            return pricingType == null || !PricingType.FIXED.getValue().equalsIgnoreCase(pricingType);
        }
        return duration >= MIN_DURATION_MINUTES;
    }
}
