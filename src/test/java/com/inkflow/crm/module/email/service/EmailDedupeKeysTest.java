package com.inkflow.crm.module.email.service;

import com.inkflow.crm.module.email.enums.TriggerType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailDedupeKeysTest {

    @Test
    void shouldReturnNullWhenEntityIdMissing() {
        assertNull(EmailDedupeKeys.forEnqueue(
                UUID.randomUUID(),
                TriggerType.BOOKING_RESCHEDULED,
                null,
                UUID.randomUUID()));
    }

    @Test
    void shouldStayWithinDedupeKeyColumnLimit() {
        String dedupeKey = EmailDedupeKeys.forEnqueue(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                TriggerType.BOOKING_RESCHEDULED,
                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                UUID.fromString("550e8400-e29b-41d4-a716-446655440002"));

        assertTrue(dedupeKey.length() <= EmailDedupeKeys.MAX_LENGTH);
        assertEquals(64, dedupeKey.length());
    }

    @Test
    void shouldProduceStableHashForSameInput() {
        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();

        String first = EmailDedupeKeys.forEnqueue(tenantId, TriggerType.BOOKING_RESCHEDULED, entityId, templateId);
        String second = EmailDedupeKeys.forEnqueue(tenantId, TriggerType.BOOKING_RESCHEDULED, entityId, templateId);

        assertEquals(first, second);
    }

    @Test
    void shouldDifferWhenTemplateChanges() {
        UUID tenantId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        String first = EmailDedupeKeys.forEnqueue(tenantId, TriggerType.BOOKING_RESCHEDULED, entityId, UUID.randomUUID());
        String second = EmailDedupeKeys.forEnqueue(tenantId, TriggerType.BOOKING_RESCHEDULED, entityId, UUID.randomUUID());

        assertNotEquals(first, second);
    }
}
