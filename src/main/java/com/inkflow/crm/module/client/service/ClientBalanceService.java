package com.inkflow.crm.module.client.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.ClientBalanceEntry;
import com.inkflow.crm.domain.enums.ClientBalanceReason;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientBalanceEntryRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.module.client.dto.ClientBalanceDto;
import com.inkflow.crm.module.client.mapper.ClientBalanceMapper;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientBalanceService {

    private static final int DEFAULT_ENTRY_PAGE_SIZE = 50;

    private final ClientRepository clientRepository;
    private final ClientBalanceEntryRepository balanceEntryRepository;
    private final AppointmentRepository appointmentRepository;
    private final ClientBalanceMapper clientBalanceMapper;

    @Transactional(readOnly = true)
    public ClientBalanceDto getClientBalance(UUID clientId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Client client = requireClient(clientId, tenantId);

        var entries = balanceEntryRepository
                .findByClientIdAndDeletedAtIsNullOrderByCreatedAtDesc(clientId,
                        PageRequest.of(0, DEFAULT_ENTRY_PAGE_SIZE))
                .map(clientBalanceMapper::toEntryDto)
                .getContent();

        return ClientBalanceDto.builder()
                .clientId(clientId)
                .balance(client.getBalance())
                .entries(entries)
                .build();
    }

    @Transactional
    public ClientBalanceEntry record(
            Client client,
            BigDecimal amount,
            ClientBalanceReason reason,
            UUID appointmentId,
            UUID transactionId,
            String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessRuleException("Balance entry amount must be non-zero");
        }

        UUID tenantId = client.getTenantId();
        UUID actorId = SecurityUtils.getCurrentUserId();

        ClientBalanceEntry entry = ClientBalanceEntry.builder()
                .tenantId(tenantId)
                .client(client)
                .amount(amount)
                .reason(reason)
                .appointmentId(appointmentId)
                .transactionId(transactionId)
                .note(note)
                .createdBy(actorId)
                .build();

        client.adjustBalance(amount);
        clientRepository.save(client);
        entry = balanceEntryRepository.save(entry);

        log.info("Client balance recorded: tenantId={} clientId={} amount={} reason={} appointmentId={}",
                tenantId, client.getId(), amount, reason.getValue(), appointmentId);

        return entry;
    }

    @Transactional
    public void chargeAppointmentOnCompletion(UUID appointmentId, UUID tenantId) {
        Appointment appointment = appointmentRepository
                .findByIdAndDeletedAtIsNull(appointmentId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(appointmentId.toString()));

        if (appointment.getClient() == null || appointment.isReservation() || appointment.getBalanceChargedAt() != null) {
            return;
        }

        BigDecimal finalPrice = appointment.getFinalPrice();
        if (finalPrice == null || finalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            appointment.setBalanceChargedAt(Instant.now());
            appointmentRepository.save(appointment);
            return;
        }

        record(
                appointment.getClient(),
                finalPrice.negate(),
                ClientBalanceReason.CHARGE,
                appointment.getId(),
                null,
                null
        );

        appointment.setBalanceChargedAt(Instant.now());
        appointmentRepository.save(appointment);
    }

    @Transactional
    public void reverseAppointmentCharge(UUID appointmentId, UUID tenantId) {
        Appointment appointment = appointmentRepository
                .findByIdAndDeletedAtIsNull(appointmentId)
                .orElseThrow(() -> ResourceNotFoundException.appointment(appointmentId.toString()));

        if (appointment.getClient() == null || appointment.isReservation() || appointment.getBalanceChargedAt() == null) {
            return;
        }

        BigDecimal finalPrice = appointment.getFinalPrice();
        if (finalPrice != null && finalPrice.compareTo(BigDecimal.ZERO) > 0) {
            record(
                    appointment.getClient(),
                    finalPrice,
                    ClientBalanceReason.CHARGE_REVERSAL,
                    appointment.getId(),
                    null,
                    null
            );
        }

        appointment.setBalanceChargedAt(null);
        appointmentRepository.save(appointment);
    }

    public BigDecimal availableCredit(Client client) {
        BigDecimal balance = client.getBalance();
        if (balance == null || balance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return balance;
    }

    public void validateBalanceSpend(Client client, BigDecimal amount) {
        BigDecimal balance = client.getBalance() != null ? client.getBalance() : BigDecimal.ZERO;
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Insufficient client balance credit");
        }
        if (amount.compareTo(balance) > 0) {
            throw new BusinessRuleException("Insufficient client balance credit");
        }
    }

    public boolean isBalanceCredit(Client client) {
        BigDecimal balance = client.getBalance();
        return balance != null && balance.compareTo(BigDecimal.ZERO) > 0;
    }

    private Client requireClient(UUID clientId, UUID tenantId) {
        return clientRepository.findByIdAndDeletedAtIsNull(clientId)
                .orElseThrow(() -> ResourceNotFoundException.client(clientId.toString()));
    }
}
