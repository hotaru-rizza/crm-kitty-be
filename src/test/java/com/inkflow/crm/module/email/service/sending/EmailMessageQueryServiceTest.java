package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.domain.entity.EmailMessage;
import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.domain.repository.EmailMessageRepository;
import com.inkflow.crm.module.email.dto.EmailMessageDto;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.mapper.EmailMessageMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailMessageQueryServiceTest {

    @Mock private EmailMessageRepository emailMessageRepository;
    @Mock private EmailMessageMapper emailMessageMapper;

    @InjectMocks
    private EmailMessageQueryService emailMessageQueryService;

    @Test
    void getMessages_mapsFilteredResults() {
        UUID tenantId = UUID.randomUUID();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-02-01T00:00:00Z");
        Pageable pageable = PageRequest.of(0, 20);
        EmailMessage message = EmailMessage.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .recipientEmail("client@test.com")
                .subject("Reminder")
                .triggerType(TriggerType.BEFORE_BOOKING)
                .status(EmailMessageStatus.SENT)
                .createdAt(Instant.parse("2026-01-15T10:00:00Z"))
                .sentAt(Instant.parse("2026-01-15T10:00:00Z"))
                .body("<html></html>")
                .build();
        EmailMessageDto dto = EmailMessageDto.builder()
                .id(message.getId())
                .recipientEmail("client@test.com")
                .subject("Reminder")
                .triggerType(TriggerType.BEFORE_BOOKING)
                .status(EmailMessageStatus.SENT)
                .sentAt(message.getSentAt())
                .build();

        when(emailMessageRepository.findFilteredWithSearch(
                tenantId, TriggerType.BEFORE_BOOKING, EmailMessageStatus.SENT, from, to, "%client%", pageable))
                .thenReturn(new PageImpl<>(List.of(message)));
        when(emailMessageMapper.toDto(message)).thenReturn(dto);

        Page<EmailMessageDto> result = emailMessageQueryService.getMessages(
                tenantId, TriggerType.BEFORE_BOOKING, EmailMessageStatus.SENT, from, to, "client", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(dto, result.getContent().getFirst());
    }
}
