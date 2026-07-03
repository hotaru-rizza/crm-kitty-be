package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.domain.entity.EmailMessage;
import com.inkflow.crm.domain.repository.EmailMessageRepository;
import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.module.email.dto.EmailMessageDto;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.mapper.EmailMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Locale;
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
            EmailMessageStatus status,
            Instant from,
            Instant to,
            String search,
            Pageable pageable) {

        String normalizedSearch = StringUtils.hasText(search) ? search.trim() : null;

        Page<EmailMessage> page = normalizedSearch == null
                ? emailMessageRepository.findFiltered( triggerType, status, from, to, pageable)
                : emailMessageRepository.findFilteredWithSearch(
                        triggerType,
                        status,
                        from,
                        to,
                        "%" + normalizedSearch.toLowerCase(Locale.ROOT) + "%",
                        pageable);

        return page.map(emailMessageMapper::toDto);
    }
}
