package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.EmailLogRepository;
import com.inkflow.crm.module.email.dto.EmailStatsDto;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.PreparedEmail;
import com.inkflow.crm.module.email.mapper.EmailLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private ResendEmailClient resendClient;

    @Mock
    private EmailLogRepository emailLogRepository;

    @Mock
    private EmailTenantContextLoader tenantContextLoader;

    @Mock
    private AppointmentEmailComposer appointmentEmailComposer;

    @Mock
    private EmailLogMapper emailLogMapper;

    @InjectMocks
    private EmailService emailService;

    @Test
    void wasAlreadySent_delegatesToRepository() {
        UUID appointmentId = UUID.randomUUID();
        when(emailLogRepository.existsByAppointmentIdAndType(appointmentId, EmailType.CONFIRMATION))
                .thenReturn(true);

        assertEquals(true, emailService.wasAlreadySent(appointmentId, EmailType.CONFIRMATION));
    }

    @Test
    void sendReminder_skipsWhenClientHasNoEmail() {
        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .client(Client.builder().firstName("John").email(null).build())
                .build();

        emailService.sendReminder(appointment, 24);

        verify(tenantContextLoader, never()).loadContext(any());
        verify(resendClient, never()).send(any(), any(), any());
    }

    @Test
    void getStats_aggregatesCounts() {
        UUID tenantId = UUID.randomUUID();

        when(emailLogRepository.countByTenantIdAndSentAtAfter(eq(tenantId), any(Instant.class)))
                .thenReturn(5L, 20L, 80L);
        when(emailLogRepository.countByTenantIdAndTypeAndSentAtAfter(eq(tenantId), any(EmailType.class), any(Instant.class)))
                .thenReturn(10L, 8L, 6L, 4L);

        EmailStatsDto stats = emailService.getStats(tenantId);

        assertEquals(5L, stats.getTotalToday());
        assertEquals(20L, stats.getTotalWeek());
        assertEquals(80L, stats.getTotalMonth());
        assertEquals(10L, stats.getConfirmationsMonth());
    }

    @Test
    void shouldPersistSentLogWhenSendManualSucceeds() {
        UUID tenantId = UUID.randomUUID();
        when(tenantContextLoader.loadContext(tenantId))
                .thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        emailService.sendManual(tenantId, "client@test.com", "Anna Ink", "Hello", "Body text");

        verify(resendClient).send(eq("client@test.com"), eq("Hello"), anyString());

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        EmailLog savedLog = logCaptor.getValue();
        assertEquals(EmailStatus.SENT, savedLog.getStatus());
        assertEquals(EmailType.MANUAL, savedLog.getType());
        assertEquals("client@test.com", savedLog.getRecipientEmail());
    }

    @Test
    void shouldPersistFailedLogWhenResendThrows() {
        UUID tenantId = UUID.randomUUID();
        when(tenantContextLoader.loadContext(tenantId))
                .thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("resend unavailable"))
                .when(resendClient).send(anyString(), anyString(), anyString());

        emailService.sendManual(tenantId, "client@test.com", "Anna Ink", "Hello", "Body text");

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        EmailLog savedLog = logCaptor.getValue();
        assertEquals(EmailStatus.FAILED, savedLog.getStatus());
        assertEquals("resend unavailable", savedLog.getErrorMessage());
    }

    @Test
    void shouldSendConfirmationWhenClientHasEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .client(Client.builder().firstName("John").lastName("Doe").email("john@test.com").build())
                .build();
        PreparedEmail prepared = new PreparedEmail("john@test.com", "John Doe", "Confirmed", "<p>Hi</p>");

        when(tenantContextLoader.loadContext(tenantId)).thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(tenantContextLoader.loadTemplateEntry(tenantId, "CONFIRMATION")).thenReturn(Map.of());
        when(appointmentEmailComposer.confirmation(eq(appointment), any(), any())).thenReturn(prepared);
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        emailService.sendConfirmation(appointment);

        verify(resendClient).send("john@test.com", "Confirmed", "<p>Hi</p>");
        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(EmailType.CONFIRMATION, logCaptor.getValue().getType());
        assertEquals(appointmentId, logCaptor.getValue().getAppointmentId());
    }

    @Test
    void shouldSkipStaffEmailWhenArtistHasNoEmail() {
        Appointment appointment = Appointment.builder()
                .tenantId(UUID.randomUUID())
                .artist(Staff.builder().firstName("Alex").lastName("Ink").email("").build())
                .build();

        emailService.sendStaffNewAppointment(appointment);

        verify(tenantContextLoader, never()).loadContext(any());
        verify(resendClient, never()).send(anyString(), anyString(), anyString());
        verify(emailLogRepository, never()).save(any());
    }

    @Test
    void shouldSkipConfirmationWhenClientEmailIsBlank() {
        Appointment appointment = Appointment.builder()
                .tenantId(UUID.randomUUID())
                .client(Client.builder().firstName("John").email("   ").build())
                .build();

        emailService.sendConfirmation(appointment);

        verify(tenantContextLoader, never()).loadContext(any());
        verify(resendClient, never()).send(anyString(), anyString(), anyString());
        verify(emailLogRepository, never()).save(any());
    }

    @Test
    void shouldSendCancellationWhenClientHasEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .client(Client.builder().firstName("Jane").lastName("Doe").email("jane@test.com").build())
                .build();
        PreparedEmail prepared = new PreparedEmail("jane@test.com", "Jane Doe", "Cancelled", "<p>Cancelled</p>");

        when(tenantContextLoader.loadContext(tenantId)).thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(tenantContextLoader.loadTemplateEntry(tenantId, "CANCELLATION")).thenReturn(Map.of());
        when(appointmentEmailComposer.cancellation(eq(appointment), any(), any())).thenReturn(prepared);
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        emailService.sendCancellation(appointment);

        verify(resendClient).send("jane@test.com", "Cancelled", "<p>Cancelled</p>");
        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(EmailType.CANCELLATION, logCaptor.getValue().getType());
    }

    @Test
    void shouldSendStaffNewAppointmentWhenArtistHasEmail() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .tenantId(tenantId)
                .artist(Staff.builder().firstName("Alex").lastName("Ink").email("artist@test.com").build())
                .build();
        PreparedEmail prepared = new PreparedEmail("artist@test.com", "Alex Ink", "New booking", "<p>New</p>");

        when(tenantContextLoader.loadContext(tenantId)).thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(appointmentEmailComposer.staffNewAppointment(eq(appointment), any())).thenReturn(prepared);
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        emailService.sendStaffNewAppointment(appointment);

        verify(resendClient).send("artist@test.com", "New booking", "<p>New</p>");
        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(EmailType.STAFF_NEW_APPOINTMENT, logCaptor.getValue().getType());
    }
}
