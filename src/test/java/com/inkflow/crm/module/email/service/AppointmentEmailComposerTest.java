package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.module.email.dto.EmailRecipient;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.NotificationCommand;
import com.inkflow.crm.module.email.enums.TemplateKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentEmailComposerTest {

    @Mock private NotificationSender notificationSender;
    @Mock private EmailTenantContextLoader tenantContextLoader;

    @InjectMocks
    private AppointmentNotificationService service;

    private static final Instant START_TIME = Instant.parse("2026-06-15T10:30:00Z");
    private static final UUID TENANT = UUID.randomUUID();

    private Appointment buildAppointment(String clientEmail) {
        return Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT)
                .client(Client.builder()
                        .firstName("Anna").lastName("Ink").email(clientEmail).build())
                .artist(Staff.builder()
                        .firstName("Alex").lastName("Tat").email("artist@test.com").build())
                .service(Service.builder().title("Tattoo").build())
                .startTime(START_TIME)
                .build();
    }

    @Test
    void sendConfirmation_callsNotificationSenderWithCorrectKey() {
        Appointment appointment = buildAppointment("anna@test.com");
        when(tenantContextLoader.loadContext(TENANT))
                .thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));

        service.sendConfirmation(appointment);

        ArgumentCaptor<NotificationCommand> commandCaptor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationSender).send(commandCaptor.capture());

        NotificationCommand command = commandCaptor.getValue();
        assertThat(command.tenantId()).isEqualTo(TENANT);
        assertThat(command.templateKey()).isEqualTo(TemplateKey.BOOKING_CONFIRMED);
        assertThat(command.recipient()).isEqualTo(EmailRecipient.of("anna@test.com", "Anna Ink"));
        assertThat(command.entityId()).isEqualTo(appointment.getId());
        assertThat(command.studioName()).isEqualTo("Ink Studio");
    }

    @Test
    void sendConfirmation_skipsWhenClientHasNoEmail() {
        Appointment appointment = buildAppointment(null);

        service.sendConfirmation(appointment);

        verifyNoInteractions(notificationSender);
    }

    @Test
    void sendStaffNewAppointment_usesNewAppointmentKey() {
        Appointment appointment = buildAppointment("anna@test.com");
        when(tenantContextLoader.loadContext(TENANT))
                .thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));

        service.sendStaffNewAppointment(appointment);

        ArgumentCaptor<NotificationCommand> commandCaptor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationSender).send(commandCaptor.capture());

        NotificationCommand command = commandCaptor.getValue();
        assertThat(command.templateKey()).isEqualTo(TemplateKey.NEW_APPOINTMENT);
        assertThat(command.recipient().email()).isEqualTo("artist@test.com");
    }

    @Test
    void sendReminder_checksIdempotencyBeforeSending() {
        Appointment appointment = buildAppointment("anna@test.com");
        when(notificationSender.wasAlreadySent(TemplateKey.BOOKING_REMINDER, appointment.getId()))
                .thenReturn(true);

        service.sendReminder(appointment, 24);

        verify(notificationSender, never()).send(org.mockito.ArgumentMatchers.any());
    }
}
