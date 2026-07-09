package com.inkflow.crm.module.transaction.mapper;

import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.module.transaction.dto.TransactionDto;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    public TransactionDto toDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .type(transaction.getType().getValue())
                .category(transaction.getCategory())
                .amount(transaction.getAmount())
                .paymentMethod(transaction.getPaymentMethod().getValue())
                .description(transaction.getDescription())
                .appointmentId(transaction.getAppointment() != null ? transaction.getAppointment().getId() : null)
                .staffId(transaction.getStaff() != null ? transaction.getStaff().getId() : null)
                .staffName(transaction.getStaff() != null ? transaction.getStaff().getFullName() : null)
                .staffAccountStatus(transaction.getStaff() != null
                        ? transaction.getStaff().getAccountStatus().getValue()
                        : null)
                .staffDeleted(transaction.getStaff() != null && transaction.getStaff().isDeleted())
                .locationId(transaction.getLocation().getId())
                .locationName(transaction.getLocation().getName())
                .date(transaction.getDate())
                .cashAmount(transaction.getCashAmount())
                .cardAmount(transaction.getCardAmount())
                .tipAmount(transaction.getTipAmount())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}
