package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.PreparedEmail;
import com.inkflow.crm.module.email.mapper.EmailTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentEmailComposerTest {

    private static final Instant START_TIME = Instant.parse("2025-06-15T10:30:00Z");
    private static final EmailTenantContext CONTEXT =
            new EmailTenantContext("Ink Studio Kyiv", "Europe/Kyiv");

    private AppointmentEmailComposer composer;

    @BeforeEach
    void setUp() {
        composer = new AppointmentEmailComposer(new EmailTemplateMapper());
    }

    @Test
    void shouldIncludeClientNameInConfirmationSubjectAndBody() {
        Appointment appointment = sampleAppointment();

        PreparedEmail email = composer.confirmation(appointment, CONTEXT, Map.of());

        assertEquals("client@example.com", email.recipientEmail());
        assertEquals("Anna Kovalenko", email.recipientName());
        assertTrue(email.subject().contains("Ink Studio Kyiv"));
        assertTrue(email.html().contains("Anna"));
        assertTrue(email.html().contains("Blackwork Sleeve"));
        assertTrue(email.html().contains("Oleksii Petrenko"));
    }

    @Test
    void shouldIncludeHoursBeforeInReminderWhenComposed() {
        Appointment appointment = sampleAppointment();
        Map<String, String> template = Map.of(
                "subject", "Нагадування — {{studio}}",
                "body", "Привіт, {{client_name}}! Через {{hours_before}}.",
                "fields", "hours_before,service,datetime"
        );

        PreparedEmail email = composer.reminder(appointment, CONTEXT, template, 24);

        assertEquals("client@example.com", email.recipientEmail());
        assertTrue(email.subject().contains("Ink Studio Kyiv"));
        assertTrue(email.html().contains("Anna"));
        assertTrue(email.html().contains("1 дн."));
        assertTrue(email.html().contains("Blackwork Sleeve"));
    }

    @Test
    void shouldTargetArtistEmailForStaffNewAppointment() {
        Appointment appointment = sampleAppointment();

        PreparedEmail email = composer.staffNewAppointment(appointment, CONTEXT);

        assertEquals("artist@example.com", email.recipientEmail());
        assertEquals("Oleksii Petrenko", email.recipientName());
        assertEquals("Новий запис — Anna Kovalenko", email.subject());
        assertTrue(email.html().contains("Oleksii"));
        assertTrue(email.html().contains("Anna Kovalenko"));
        assertTrue(email.html().contains("Blackwork Sleeve"));
    }

    @Test
    void shouldUseStudioNameInCancellationSubjectAndBody() {
        Appointment appointment = sampleAppointment();

        PreparedEmail email = composer.cancellation(appointment, CONTEXT, Map.of());

        assertEquals("client@example.com", email.recipientEmail());
        assertTrue(email.subject().contains("Ink Studio Kyiv"));
        assertTrue(email.html().contains("Anna"));
        assertTrue(email.html().contains("Blackwork Sleeve"));
        assertTrue(email.html().contains("15 червня 2025, 13:30"));
    }

    @Test
    void shouldIncludeArtistAndNewDatetimeInRescheduleEmail() {
        Appointment appointment = sampleAppointment();

        PreparedEmail email = composer.reschedule(appointment, CONTEXT, Map.of());

        assertEquals("client@example.com", email.recipientEmail());
        assertTrue(email.subject().contains("Ink Studio Kyiv"));
        assertTrue(email.html().contains("Anna"));
        assertTrue(email.html().contains("Oleksii Petrenko"));
        assertTrue(email.html().contains("15 червня 2025, 13:30"));
    }

    @Test
    void shouldComposeAftercareWithCustomTemplateEntry() {
        Appointment appointment = sampleAppointment();
        Map<String, String> template = Map.of(
                "subject", "Догляд — {{studio}}",
                "body", "Дякуємо, {{client_name}}, за {{service}}.",
                "fields", "service"
        );

        PreparedEmail email = composer.aftercare(appointment, CONTEXT, template);

        assertEquals("client@example.com", email.recipientEmail());
        assertTrue(email.subject().contains("Ink Studio Kyiv"));
        assertTrue(email.html().contains("Anna"));
        assertTrue(email.html().contains("Blackwork Sleeve"));
    }

    private Appointment sampleAppointment() {
        return Appointment.builder()
                .client(Client.builder()
                        .firstName("Anna")
                        .lastName("Kovalenko")
                        .email("client@example.com")
                        .build())
                .artist(Staff.builder()
                        .firstName("Oleksii")
                        .lastName("Petrenko")
                        .email("artist@example.com")
                        .build())
                .service(Service.builder()
                        .title("Blackwork Sleeve")
                        .build())
                .startTime(START_TIME)
                .build();
    }
}
