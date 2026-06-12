package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.EmailLogRepository;
import com.inkflow.crm.module.email.dto.EmailLogDto;
import com.inkflow.crm.module.email.dto.EmailStatsDto;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private ResendEmailClient resendClient;
    @Mock private EmailLogRepository emailLogRepository;
    @Mock private EmailTenantContextLoader tenantContextLoader;
    @Mock private EmailLogMapper emailLogMapper;
    @Mock private InkflowProperties inkflowProperties;

    @InjectMocks
    private EmailService emailService;

    @Test
    void sendManual_sendsEmailAndLogsSuccess() {
        UUID tenantId = UUID.randomUUID();
        when(inkflowProperties.getAppName()).thenReturn("CRM");
        when(tenantContextLoader.loadContext(tenantId))
                .thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(emailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        emailService.sendManual(tenantId, "client@test.com", "Anna Ink", "Hello", "Body text");

        verify(resendClient).send(eq("client@test.com"), eq("Hello"), anyString());

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(EmailStatus.SENT, logCaptor.getValue().getStatus());
        assertEquals(EmailType.MANUAL, logCaptor.getValue().getType());
        assertEquals("client@test.com", logCaptor.getValue().getRecipientEmail());
    }

    @Test
    void sendManual_logsFailureWhenResendThrows() {
        UUID tenantId = UUID.randomUUID();
        when(inkflowProperties.getAppName()).thenReturn("CRM");
        when(tenantContextLoader.loadContext(tenantId))
                .thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv"));
        when(emailLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("resend unavailable"))
                .when(resendClient).send(anyString(), anyString(), anyString());

        emailService.sendManual(tenantId, "client@test.com", "Anna", "Hello", "Body text");

        ArgumentCaptor<EmailLog> logCaptor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertEquals(EmailStatus.FAILED, logCaptor.getValue().getStatus());
        assertEquals("resend unavailable", logCaptor.getValue().getErrorMessage());
    }

    @Test
    void getLog_mapsFilteredResults() {
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
    }

    @Test
    void getLog_handlesNullFilters() {
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
}
