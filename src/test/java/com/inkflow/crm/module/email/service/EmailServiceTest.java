package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.EmailLogRepository;
import com.inkflow.crm.module.email.dto.EmailLogDto;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    void shouldMapFilteredLogsToDtoWhenGetLog() {
        UUID tenantId = UUID.randomUUID();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        Pageable pageable = PageRequest.of(0, 20);
        EmailLog log = EmailLog.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .recipientEmail("client@test.com")
                .subject("Reminder")
                .type(EmailType.REMINDER)
                .status(EmailStatus.SENT)
                .sentAt(Instant.parse("2026-01-15T10:00:00Z"))
                .build();
        EmailLogDto dto = EmailLogDto.builder()
                .id(log.getId())
                .recipientEmail("client@test.com")
                .subject("Reminder")
                .type(EmailType.REMINDER)
                .status(EmailStatus.SENT)
                .sentAt(log.getSentAt())
                .build();

        when(emailLogRepository.findFiltered(tenantId, EmailType.REMINDER, from, to, pageable))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(emailLogMapper.toDto(log)).thenReturn(dto);

        Page<EmailLogDto> result = emailService.getLog(tenantId, EmailType.REMINDER, from, to, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(dto, result.getContent().getFirst());
        verify(emailLogRepository).findFiltered(tenantId, EmailType.REMINDER, from, to, pageable);
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
        assertEquals(8L, stats.getRemindersMonth());
        assertEquals(6L, stats.getAftercareMonth());
        assertEquals(4L, stats.getManualMonth());
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

    @Test
    void shouldSkipCancellationWhenClientHasNoEmail() {
        Appointment appointment = Appointment.builder()
                .tenantId(UUID.randomUUID())
                .client(Client.builder().firstName("Jane").email(null).build())
                .build();

        emailService.sendCancellation(appointment);

        verify(tenantContextLoader, never()).loadContext(any());
        verify(resendClient, never()).send(anyString(), anyString(), anyString());
        verify(emailLogRepository, never()).save(any());
    }

    @Test
    void shouldPersistFailedLogWhenCancellationSendFails() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .client(Client.builder().firstName("Jane").email("jane@test.com").build())
                .build();
        PreparedEmail prepared = new PreparedEmail("jane@test.com", "Jane", "Cancelled", "<p>Cancelled</p>");

        when(tenantContextLoader.loadContext(tenantId)).thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(tenantContextLoader.loadTemplateEntry(tenantId, "CANCELLATION")).thenReturn(Map.of());
        when(appointmentEmailComposer.cancellation(eq(appointment), any(), any())).thenReturn(prepared);
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("resend down")).when(resendClient).send(anyString(), anyString(), anyString());

        emailService.sendCancellation(appointment);

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(EmailStatus.FAILED, logCaptor.getValue().getStatus());
        assertEquals(EmailType.CANCELLATION, logCaptor.getValue().getType());
        assertEquals("resend down", logCaptor.getValue().getErrorMessage());
    }

    @Test
    void shouldSendStaffCancellationWhenArtistHasEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .artist(Staff.builder().firstName("Alex").lastName("Ink").email("artist@test.com").build())
                .build();
        PreparedEmail prepared = new PreparedEmail("artist@test.com", "Alex Ink", "Cancelled booking", "<p>Cancelled</p>");

        when(tenantContextLoader.loadContext(tenantId)).thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(appointmentEmailComposer.staffCancellation(eq(appointment), any())).thenReturn(prepared);
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        emailService.sendStaffCancellation(appointment);

        verify(resendClient).send("artist@test.com", "Cancelled booking", "<p>Cancelled</p>");
        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(EmailType.STAFF_CANCELLATION, logCaptor.getValue().getType());
        assertEquals(appointmentId, logCaptor.getValue().getAppointmentId());
    }

    @Test
    void shouldSendStaffRescheduleWhenArtistHasEmail() {
        UUID tenantId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .tenantId(tenantId)
                .artist(Staff.builder().firstName("Alex").lastName("Ink").email("artist@test.com").build())
                .build();
        PreparedEmail prepared = new PreparedEmail("artist@test.com", "Alex Ink", "Rescheduled", "<p>Moved</p>");

        when(tenantContextLoader.loadContext(tenantId)).thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(appointmentEmailComposer.staffReschedule(eq(appointment), any())).thenReturn(prepared);
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        emailService.sendStaffReschedule(appointment);

        verify(resendClient).send("artist@test.com", "Rescheduled", "<p>Moved</p>");
        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(EmailType.STAFF_RESCHEDULE, logCaptor.getValue().getType());
    }

    @Test
    void shouldSkipStaffRescheduleWhenArtistEmailIsNull() {
        Appointment appointment = Appointment.builder()
                .tenantId(UUID.randomUUID())
                .artist(Staff.builder().firstName("Alex").email(null).build())
                .build();

        emailService.sendStaffReschedule(appointment);

        verify(tenantContextLoader, never()).loadContext(any());
        verify(resendClient, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void shouldReturnAllLogsWhenGetLogFiltersAreNull() {
        UUID tenantId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        EmailLog log = EmailLog.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .recipientEmail("any@test.com")
                .type(EmailType.MANUAL)
                .status(EmailStatus.SENT)
                .build();
        EmailLogDto dto = EmailLogDto.builder().id(log.getId()).recipientEmail("any@test.com").build();

        when(emailLogRepository.findFiltered(eq(tenantId), isNull(), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(emailLogMapper.toDto(log)).thenReturn(dto);

        Page<EmailLogDto> result = emailService.getLog(tenantId, null, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(dto, result.getContent().getFirst());
        verify(emailLogRepository).findFiltered(tenantId, null, null, null, pageable);
    }

    @Test
    void shouldSendReminderWhenClientHasEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .client(Client.builder().firstName("John").email("john@test.com").build())
                .build();
        PreparedEmail prepared = new PreparedEmail("john@test.com", "John", "Reminder", "<p>See you soon</p>");

        when(tenantContextLoader.loadContext(tenantId)).thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(tenantContextLoader.loadTemplateEntry(tenantId, "REMINDER")).thenReturn(Map.of());
        when(appointmentEmailComposer.reminder(eq(appointment), any(), any(), eq(24))).thenReturn(prepared);
        when(emailLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        emailService.sendReminder(appointment, 24);

        verify(resendClient).send("john@test.com", "Reminder", "<p>See you soon</p>");
        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(EmailType.REMINDER, logCaptor.getValue().getType());
        assertEquals(appointmentId, logCaptor.getValue().getAppointmentId());
        assertEquals(EmailStatus.SENT, logCaptor.getValue().getStatus());
    }

    @Test
    void shouldFilterGetLogByTypeOnly() {
        UUID tenantId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        EmailLog log = EmailLog.builder()
                .id(UUID.randomUUID())
                .type(EmailType.CONFIRMATION)
                .status(EmailStatus.SENT)
                .build();

        when(emailLogRepository.findFiltered(tenantId, EmailType.CONFIRMATION, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(log)));
        when(emailLogMapper.toDto(log)).thenReturn(EmailLogDto.builder().type(EmailType.CONFIRMATION).build());

        Page<EmailLogDto> result = emailService.getLog(tenantId, EmailType.CONFIRMATION, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(EmailType.CONFIRMATION, result.getContent().getFirst().getType());
    }
}
