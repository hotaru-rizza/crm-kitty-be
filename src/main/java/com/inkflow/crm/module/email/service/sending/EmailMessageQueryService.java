package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.domain.repository.EmailMessageRepository;
import com.inkflow.crm.module.email.dto.EmailMessageDto;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.mapper.EmailMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailMessageQueryService {

    private final EmailMessageRepository emailMessageRepository;
    private final EmailMessageMapper emailMessageMapper;

    @Transactional(readOnly = true)
    public Page<EmailMessageDto> getMessages(
            UUID tenantId,
            TriggerType triggerType,
            Instant from,
            Instant to,
            Pageable pageable) {

        return emailMessageRepository.findFiltered(tenantId, triggerType, from, to, pageable)
                .map(emailMessageMapper::toDto);
    }
}
