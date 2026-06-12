package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.repository.EmailLogRepository;
import com.inkflow.crm.module.email.dto.EmailRecipient;
import com.inkflow.crm.module.email.dto.NotificationCommand;
import com.inkflow.crm.module.email.dto.RenderedEmail;
import com.inkflow.crm.module.email.enums.TemplateKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.domain.enums.EmailStatus;

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
    @Mock private EmailLogRepository emailLogRepository;

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
                "Ink Studio"
        );
    }

    @Test
    void send_callsResendAndLogsSuccess() {
        when(contentRenderer.render(any(NotificationCommand.class)))
                .thenReturn(new RenderedEmail("Booking confirmed", "<html>body</html>"));

        notificationSender.send(bookingConfirmedCommand);

        verify(resendClient).send(eq("client@test.com"), eq("Booking confirmed"), eq("<html>body</html>"));

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());

        EmailLog log = logCaptor.getValue();
        assertThat(log.getStatus()).isEqualTo(EmailStatus.SENT);
        assertThat(log.getTemplateKey()).isEqualTo(TemplateKey.BOOKING_CONFIRMED.name());
        assertThat(log.getEntityId()).isEqualTo(ENTITY);
        assertThat(log.getRecipientEmail()).isEqualTo("client@test.com");
    }

    @Test
    void send_logsFailureWhenResendThrows() {
        when(contentRenderer.render(any(NotificationCommand.class)))
                .thenReturn(new RenderedEmail("Subject", "<html>body</html>"));
        doThrow(new RuntimeException("SMTP error")).when(resendClient).send(anyString(), anyString(), anyString());

        notificationSender.send(bookingConfirmedCommand);

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());

        assertThat(logCaptor.getValue().getStatus()).isEqualTo(EmailStatus.FAILED);
        assertThat(logCaptor.getValue().getErrorMessage()).contains("SMTP error");
    }

    @Test
    void send_skipsWhenRecipientEmailIsBlank() {
        NotificationCommand command = NotificationCommand.forTenant(
                TENANT,
                EmailRecipient.of("", "NoEmail"),
                TemplateKey.BOOKING_CONFIRMED,
                Map.of(),
                null,
                "Studio"
        );

        notificationSender.send(command);

        verifyNoInteractions(contentRenderer, resendClient, emailLogRepository);
    }

    @Test
    void wasAlreadySent_delegatesToRepository() {
        when(emailLogRepository.existsByTemplateKeyAndEntityId(TemplateKey.BOOKING_REMINDER.name(), ENTITY))
                .thenReturn(true);

        assertThat(notificationSender.wasAlreadySent(TemplateKey.BOOKING_REMINDER, ENTITY)).isTrue();
    }

    @Test
    void wasAlreadySent_returnsFalseForNullEntityId() {
        assertThat(notificationSender.wasAlreadySent(TemplateKey.BOOKING_REMINDER, null)).isFalse();
        verifyNoInteractions(emailLogRepository);
    }
}
