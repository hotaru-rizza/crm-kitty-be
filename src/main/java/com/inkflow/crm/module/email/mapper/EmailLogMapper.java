package com.inkflow.crm.module.email.mapper;

import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.module.email.dto.EmailLogDto;
import org.springframework.stereotype.Component;

@Component
public class EmailLogMapper {

    public EmailLogDto toDto(EmailLog log) {
        return EmailLogDto.builder()
                .id(log.getId())
                .recipientEmail(log.getRecipientEmail())
                .recipientName(log.getRecipientName())
                .subject(log.getSubject())
                .type(log.getType())
                .status(log.getStatus())
                .errorMessage(log.getErrorMessage())
                .sentAt(log.getSentAt())
                .build();
    }
}
