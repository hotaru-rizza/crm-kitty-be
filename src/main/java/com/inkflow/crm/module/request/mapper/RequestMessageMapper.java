package com.inkflow.crm.module.request.mapper;

import com.inkflow.crm.domain.entity.RequestMessage;
import com.inkflow.crm.module.request.dto.RequestMessageDto;
import org.springframework.stereotype.Component;

@Component
public class RequestMessageMapper {

    public RequestMessageDto toDto(RequestMessage message) {
        return RequestMessageDto.builder()
                .id(message.getId())
                .senderType(message.getSenderType().getValue())
                .senderName(message.getSenderName())
                .body(message.getBody())
                .imageUrl(message.getImageUrl())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
