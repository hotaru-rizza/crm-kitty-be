package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.EmailMessage;
import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.domain.enums.SupportedLocale;
import com.inkflow.crm.domain.repository.EmailMessageRepository;
import com.inkflow.crm.module.email.dto.EmailRecipient;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.NotificationCommand;
import com.inkflow.crm.module.email.dto.RenderedEmail;
import com.inkflow.crm.module.email.enums.TemplateKey;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.service.sending.NotificationSender;
import com.inkflow.crm.module.email.service.sending.ResendEmailClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSenderTest {

    @Mock private EmailContentRenderer contentRenderer;
    @Mock private ResendEmailClient resendClient;
    @Mock private EmailMessageRepository emailMessageRepository;

    @InjectMocks
    private NotificationSender notificationSender;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ENTITY = UUID.randomUUID();

    private NotificationCommand bookingConfirmedCommand;

    @BeforeEach
    void setUp() {
        bookingConfirmedCommand = NotificationCommand.forTenant(
                TENANT,
                EmailRecipient.of("client@test.com", "Anna"),
                TemplateKey.BOOKING_CONFIRMED,
                Map.of("client_name", "Anna"),
                ENTITY,
                new EmailTenantContext("Ink Studio", null, "Europe/Kyiv", SupportedLocale.UK)
        );
    }

    @Test
    void send_callsResendAndLogsSuccess() {
        when(contentRenderer.render(any(NotificationCommand.class)))
                .thenReturn(new RenderedEmail("Booking confirmed", "<html>body</html>"));

        notificationSender.send(bookingConfirmedCommand);

        verify(resendClient).send(eq("client@test.com"), eq("Booking confirmed"), eq("<html>body</html>"));

        ArgumentCaptor<EmailMessage> logCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailMessageRepository).save(logCaptor.capture());

        EmailMessage log = logCaptor.getValue();
        assertThat(log.getStatus()).isEqualTo(EmailMessageStatus.SENT);
        assertThat(log.getTriggerType()).isEqualTo(TriggerType.BOOKING_CONFIRMED);
        assertThat(log.getEntityId()).isEqualTo(ENTITY);
        assertThat(log.getRecipientEmail()).isEqualTo("client@test.com");
    }

    @Test
    void send_logsFailureWhenResendThrows() {
        when(contentRenderer.render(any(NotificationCommand.class)))
                .thenReturn(new RenderedEmail("Subject", "<html>body</html>"));
        doThrow(new RuntimeException("SMTP error")).when(resendClient).send(anyString(), anyString(), anyString());

        notificationSender.send(bookingConfirmedCommand);

        ArgumentCaptor<EmailMessage> logCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailMessageRepository).save(logCaptor.capture());

        assertThat(logCaptor.getValue().getStatus()).isEqualTo(EmailMessageStatus.FAILED);
        assertThat(logCaptor.getValue().getLastError()).contains("SMTP error");
    }

    @Test
    void send_skipsWhenRecipientEmailIsBlank() {
        NotificationCommand command = NotificationCommand.forTenant(
                TENANT,
                EmailRecipient.of("", "NoEmail"),
                TemplateKey.BOOKING_CONFIRMED,
                Map.of(),
                null,
                new EmailTenantContext("Studio", null, "Europe/Kyiv", SupportedLocale.UK)
        );

        notificationSender.send(command);

        verifyNoInteractions(contentRenderer, resendClient, emailMessageRepository);
    }

    @Test
    void wasAlreadySent_delegatesToRepository() {
        when(emailMessageRepository.existsByTriggerTypeAndEntityIdAndStatus(
                TriggerType.BEFORE_BOOKING, ENTITY, EmailMessageStatus.SENT))
                .thenReturn(true);

        assertThat(notificationSender.wasAlreadySent(TemplateKey.BOOKING_REMINDER, ENTITY)).isTrue();
    }

    @Test
    void wasAlreadySent_returnsFalseForNullEntityId() {
        assertThat(notificationSender.wasAlreadySent(TemplateKey.BOOKING_REMINDER, null)).isFalse();
        verifyNoInteractions(emailMessageRepository);
    }

    @Test
    void send_mapsStaffAppointmentTemplateToStaffTrigger() {
        NotificationCommand command = NotificationCommand.forTenant(
                TENANT,
                EmailRecipient.of("artist@test.com", "Mykyta"),
                TemplateKey.NEW_APPOINTMENT,
                Map.of("client_name", "Anna"),
                ENTITY,
                new EmailTenantContext("Ink Studio", null, "Europe/Kyiv", SupportedLocale.UK)
        );
        when(contentRenderer.render(any(NotificationCommand.class)))
                .thenReturn(new RenderedEmail("New appointment", "<html>body</html>"));

        notificationSender.send(command);

        ArgumentCaptor<EmailMessage> logCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailMessageRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getTriggerType()).isEqualTo(TriggerType.STAFF_APPOINTMENT);
    }
}
