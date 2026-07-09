package com.inkflow.crm.module.service.support;

import com.inkflow.crm.domain.enums.PricingType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDurationPolicyTest {

    @Test
    void resolveForCreate_fixed_usesProvidedDuration() {
        assertEquals(30, ServiceDurationPolicy.resolveForCreate(PricingType.FIXED, 30));
    }

    @Test
    void resolveForCreate_hourlyWithoutDuration_defaultsToSixty() {
        assertEquals(60, ServiceDurationPolicy.resolveForCreate(PricingType.HOURLY, null));
        assertEquals(60, ServiceDurationPolicy.resolveForCreate(PricingType.HOURLY, 0));
    }

    @Test
    void resolveForCreate_projectWithCustomDuration_keepsValue() {
        assertEquals(120, ServiceDurationPolicy.resolveForCreate(PricingType.PROJECT, 120));
    }

    @Test
    void isDurationValidForCreate_fixedRequiresMinimum() {
        assertFalse(ServiceDurationPolicy.isDurationValidForCreate("fixed", null));
        assertFalse(ServiceDurationPolicy.isDurationValidForCreate("fixed", 10));
        assertTrue(ServiceDurationPolicy.isDurationValidForCreate("fixed", 30));
    }

    @Test
    void isDurationValidForCreate_hourlyAllowsMissingDuration() {
        assertTrue(ServiceDurationPolicy.isDurationValidForCreate("hourly", null));
        assertTrue(ServiceDurationPolicy.isDurationValidForCreate("hourly", 0));
        assertFalse(ServiceDurationPolicy.isDurationValidForCreate("hourly", 10));
    }
}
