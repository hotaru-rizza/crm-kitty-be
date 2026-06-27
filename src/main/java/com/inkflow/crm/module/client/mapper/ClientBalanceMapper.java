package com.inkflow.crm.module.client.mapper;

import com.inkflow.crm.domain.entity.ClientBalanceEntry;
import com.inkflow.crm.module.client.dto.ClientBalanceEntryDto;
import org.springframework.stereotype.Component;

@Component
public class ClientBalanceMapper {

    public ClientBalanceEntryDto toEntryDto(ClientBalanceEntry entry) {
        return ClientBalanceEntryDto.builder()
                .id(entry.getId())
                .amount(entry.getAmount())
                .reason(entry.getReason().getValue())
                .appointmentId(entry.getAppointmentId())
                .transactionId(entry.getTransactionId())
                .note(entry.getNote())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
