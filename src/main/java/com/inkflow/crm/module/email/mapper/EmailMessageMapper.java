package com.inkflow.crm.module.email.mapper;

import com.inkflow.crm.domain.entity.EmailMessage;
import com.inkflow.crm.module.email.dto.EmailMessageDto;
import org.springframework.stereotype.Component;

@Component
public class EmailMessageMapper {

    public EmailMessageDto toDto(EmailMessage message) {
        return EmailMessageDto.builder()
                .id(message.getId())
                .templateId(message.getTemplateId())
                .triggerType(message.getTriggerType())
                .recipientEmail(message.getRecipientEmail())
                .recipientName(message.getRecipientName())
                .subject(message.getSubject())
                .status(message.getStatus())
                .lastError(message.getLastError())
                .createdAt(message.getCreatedAt())
                .sentAt(message.getSentAt())
                .build();
    }
}
