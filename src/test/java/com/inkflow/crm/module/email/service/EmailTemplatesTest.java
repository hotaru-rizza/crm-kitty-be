package com.inkflow.crm.module.email.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplatesTest {

    private static final Instant START_TIME = Instant.parse("2025-06-15T10:30:00Z");
    private static final String TIMEZONE = "Europe/Kyiv";
    private static final String STUDIO = "Ink Studio Kyiv";

    @Test
    void shouldSubstituteClientNameAndStudioInConfirmation() {
        String html = EmailTemplates.confirmation(
                "Anna",
                "Blackwork Sleeve",
                "Oleksii Petrenko",
                START_TIME,
                TIMEZONE,
                STUDIO,
                null,
                null,
                null
        );

        assertTrue(html.contains("Anna"));
        assertTrue(html.contains(STUDIO));
        assertTrue(html.contains("INKAT"));
        assertTrue(html.contains("Запис підтверджено"));
    }

    @Test
    void shouldRenderServiceArtistAndDatetimeInConfirmationInfoBlock() {
        String html = EmailTemplates.confirmation(
                "Anna",
                "Blackwork Sleeve",
                "Oleksii Petrenko",
                START_TIME,
                TIMEZONE,
                STUDIO,
                null,
                null,
                List.of("service", "artist", "datetime")
        );

        assertTrue(html.contains("Blackwork Sleeve"));
        assertTrue(html.contains("Oleksii Petrenko"));
        assertTrue(html.contains("15 червня 2025, 13:30"));
        assertTrue(html.contains("Послуга"));
        assertTrue(html.contains("Майстер"));
    }

    @Test
    void shouldUseDaysLabelWhenReminderIs24HoursOrMore() {
        String html = EmailTemplates.reminder(
                "Anna",
                "Consultation",
                "Oleksii Petrenko",
                START_TIME,
                TIMEZONE,
                STUDIO,
                48,
                null,
                "Нагадування через {{hours_before}} — {{client_name}}",
                List.of("hours_before")
        );

        assertTrue(html.contains("2 дн."));
        assertTrue(html.contains("Anna"));
    }

    @Test
    void shouldUseHoursLabelWhenReminderIsLessThan24Hours() {
        String html = EmailTemplates.reminder(
                "Anna",
                "Consultation",
                "Oleksii Petrenko",
                START_TIME,
                TIMEZONE,
                STUDIO,
                3,
                null,
                "Через {{hours_before}} — {{client_name}}",
                List.of("hours_before")
        );

        assertTrue(html.contains("3 год."));
        assertTrue(html.contains("Anna"));
    }

    @Test
    void shouldRenderAftercareBulletListAsHtml() {
        String html = EmailTemplates.aftercare(
                "Anna",
                "Blackwork Sleeve",
                STUDIO,
                null,
                null,
                List.of("service")
        );

        assertTrue(html.contains("<ul"));
        assertTrue(html.contains("Не знімайте захисну плівку"));
        assertTrue(html.contains("Blackwork Sleeve"));
        assertTrue(html.contains("Anna"));
    }

    @Test
    void shouldSubstituteVariablesInCustomCancellationBody() {
        String html = EmailTemplates.cancellation(
                "Anna",
                "Cover-up",
                START_TIME,
                TIMEZONE,
                STUDIO,
                "Скасування для {{client_name}}",
                "Шкода, {{client_name}}, запис на {{service}} скасовано."
        );

        assertTrue(html.contains("Anna"));
        assertTrue(html.contains("Cover-up"));
        assertTrue(html.contains("Скасування для Anna"));
        assertTrue(html.contains("15 червня 2025, 13:30"));
    }

    @Test
    void shouldIncludeNewDatetimeInRescheduleEmail() {
        String html = EmailTemplates.reschedule(
                "Anna",
                "Cover-up",
                "Oleksii Petrenko",
                START_TIME,
                TIMEZONE,
                STUDIO,
                null,
                null
        );

        assertTrue(html.contains("Anna"));
        assertTrue(html.contains("Cover-up"));
        assertTrue(html.contains("Oleksii Petrenko"));
        assertTrue(html.contains("15 червня 2025, 13:30"));
        assertTrue(html.contains("Час запису змінено"));
    }

    @Test
    void shouldReturnManagedDefaultsAndFields() {
        Map<String, String> confirmationDefaults = EmailTemplates.getDefaults("CONFIRMATION");
        List<String> confirmationFields = EmailTemplates.getDefaultFields("CONFIRMATION");

        assertEquals("Запис підтверджено — {{studio}}", confirmationDefaults.get("subject"));
        assertTrue(confirmationDefaults.get("body").contains("{{client_name}}"));
        assertEquals(List.of("service", "artist", "datetime"), confirmationFields);
        assertTrue(EmailTemplates.getDefaultFields("UNKNOWN").isEmpty());
        assertTrue(EmailTemplates.getDefaults("UNKNOWN").get("subject").isBlank());
    }

    @Test
    void shouldWrapManualEmailWithStudioFooter() {
        String html = EmailTemplates.manual("Promo", "Hello client", STUDIO);

        assertTrue(html.contains("Promo"));
        assertTrue(html.contains("Hello client"));
        assertTrue(html.contains(STUDIO));
        assertFalse(html.contains("{{"));
    }

}
