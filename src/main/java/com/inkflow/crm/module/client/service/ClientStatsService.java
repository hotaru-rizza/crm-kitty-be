package com.inkflow.crm.module.client.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientStatsService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ClientRepository clientRepository;
    private final AppointmentRepository appointmentRepository;

    @Transactional
    public void applyStatusTransition(Appointment appointment, AppointmentStatus previous, AppointmentStatus current) {
        if (previous == current) {
            return;
        }

        Client clientRef = appointment.getClient();
        if (clientRef == null || appointment.isReservation()) {
            return;
        }

        Client client = clientRepository.findByIdAndDeletedAtIsNull(clientRef.getId())
                .orElseThrow(() -> ResourceNotFoundException.client(clientRef.getId().toString()));

        BigDecimal ltvAmount = appointment.getFinalPrice() != null ? appointment.getFinalPrice() : BigDecimal.ZERO;

        if (current == AppointmentStatus.COMPLETED && previous != AppointmentStatus.COMPLETED) {
            client.incrementVisits();
            addLtvIfPositive(client, ltvAmount);
        } else if (previous == AppointmentStatus.COMPLETED && current != AppointmentStatus.COMPLETED) {
            client.decrementVisits();
            subtractLtvIfPositive(client, ltvAmount);
        }

        if (current == AppointmentStatus.CANCELLED && previous != AppointmentStatus.CANCELLED) {
            client.incrementCancelledVisits();
        } else if (previous == AppointmentStatus.CANCELLED && current != AppointmentStatus.CANCELLED) {
            client.decrementCancelledVisits();
        }

        clientRepository.save(client);

        log.info("Client stats updated: tenantId={} clientId={} status={} -> {} totalVisits={} cancelledVisits={} ltv={}",
                client.getTenantId(),
                client.getId(),
                previous.getValue(),
                current.getValue(),
                client.getTotalVisits(),
                client.getCancelledVisits(),
                client.getLtv());
    }

    @Transactional
    public void syncFromAppointments(UUID clientId) {
        Client client = clientRepository.findByIdAndDeletedAtIsNull(clientId)
                .orElseThrow(() -> ResourceNotFoundException.client(clientId.toString()));

        int completedVisits = (int) appointmentRepository.countByClientIdAndStatusAndDeletedAtIsNull(
                clientId, AppointmentStatus.COMPLETED);
        int cancelledVisits = (int) appointmentRepository.countByClientIdAndStatusAndDeletedAtIsNull(
                clientId, AppointmentStatus.CANCELLED);
        BigDecimal ltv = appointmentRepository.sumCompletedRevenueByClientId(clientId);
        if (ltv == null) {
            ltv = ZERO;
        }

        client.setTotalVisits(completedVisits);
        client.setCancelledVisits(cancelledVisits);
        client.setLtv(ltv);
        appointmentRepository.findLastCompletedStartTimeByClientId(clientId)
                .ifPresent(client::setLastVisit);

        clientRepository.save(client);

        log.info("Client stats synced from appointments: tenantId={} clientId={} totalVisits={} cancelledVisits={} ltv={}",
                client.getTenantId(), clientId, completedVisits, cancelledVisits, ltv);
    }

    private void addLtvIfPositive(Client client, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            client.addToLtv(amount);
        }
    }

    private void subtractLtvIfPositive(Client client, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            client.addToLtv(amount.negate());
        }
    }
}
