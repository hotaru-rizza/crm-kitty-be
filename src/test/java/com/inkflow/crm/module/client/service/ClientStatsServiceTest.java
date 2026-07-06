package com.inkflow.crm.module.client.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientStatsServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private ClientStatsService clientStatsService;

    @Test
    void syncFromAppointments_recalculatesCountersFromAppointmentRows() {
        Client client = client(0, 0, BigDecimal.ZERO);
        when(clientRepository.findByIdAndDeletedAtIsNull(client.getId())).thenReturn(Optional.of(client));
        when(appointmentRepository.countByClientIdAndStatusAndDeletedAtIsNull(
                client.getId(), AppointmentStatus.COMPLETED)).thenReturn(2L);
        when(appointmentRepository.countByClientIdAndStatusAndDeletedAtIsNull(
                client.getId(), AppointmentStatus.CANCELLED)).thenReturn(0L);
        when(appointmentRepository.sumCompletedRevenueByClientId(client.getId()))
                .thenReturn(new BigDecimal("4500.00"));
        when(appointmentRepository.findLastCompletedStartTimeByClientId(client.getId()))
                .thenReturn(Optional.empty());

        clientStatsService.syncFromAppointments(client.getId());

        assertThat(client.getTotalVisits()).isEqualTo(2);
        assertThat(client.getLtv()).isEqualByComparingTo("4500.00");
        verify(clientRepository).save(client);
    }

    @Test
    void applyStatusTransition_completed_incrementsVisitsAndLtv() {
        Client client = client(0, 0, BigDecimal.ZERO);
        Appointment appointment = appointment(client, new BigDecimal("4500.00"));
        when(clientRepository.findByIdAndDeletedAtIsNull(client.getId())).thenReturn(Optional.of(client));

        clientStatsService.applyStatusTransition(
                appointment,
                AppointmentStatus.SCHEDULED,
                AppointmentStatus.COMPLETED);

        assertThat(client.getTotalVisits()).isEqualTo(1);
        assertThat(client.getLtv()).isEqualByComparingTo("4500.00");
        verify(clientRepository).save(client);
    }

    @Test
    void applyStatusTransition_cancelled_incrementsCancelledVisits() {
        Client client = client(1, 0, BigDecimal.ZERO);
        Appointment appointment = appointment(client, BigDecimal.ZERO);
        when(clientRepository.findByIdAndDeletedAtIsNull(client.getId())).thenReturn(Optional.of(client));

        clientStatsService.applyStatusTransition(
                appointment,
                AppointmentStatus.SCHEDULED,
                AppointmentStatus.CANCELLED);

        assertThat(client.getCancelledVisits()).isEqualTo(1);
        assertThat(client.getTotalVisits()).isEqualTo(1);
        verify(clientRepository).save(client);
    }

    @Test
    void applyStatusTransition_restoreFromCompleted_revertsVisitsAndLtv() {
        Client client = client(2, 0, new BigDecimal("4500.00"));
        Appointment appointment = appointment(client, new BigDecimal("4500.00"));
        when(clientRepository.findByIdAndDeletedAtIsNull(client.getId())).thenReturn(Optional.of(client));

        clientStatsService.applyStatusTransition(
                appointment,
                AppointmentStatus.COMPLETED,
                AppointmentStatus.SCHEDULED);

        assertThat(client.getTotalVisits()).isEqualTo(1);
        assertThat(client.getLtv()).isEqualByComparingTo("0.00");
        verify(clientRepository).save(client);
    }

    @Test
    void applyStatusTransition_skipsReservation() {
        Client client = client(0, 0, BigDecimal.ZERO);
        Appointment appointment = appointment(client, BigDecimal.ZERO);
        appointment.setReservation(true);

        clientStatsService.applyStatusTransition(
                appointment,
                AppointmentStatus.SCHEDULED,
                AppointmentStatus.COMPLETED);

        verify(clientRepository, never()).save(any());
    }

    private Client client(int totalVisits, int cancelledVisits, BigDecimal ltv) {
        return Client.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .firstName("Test")
                .lastName("Client")
                .totalVisits(totalVisits)
                .cancelledVisits(cancelledVisits)
                .ltv(ltv)
                .balance(BigDecimal.ZERO)
                .build();
    }

    private Appointment appointment(Client client, BigDecimal finalPrice) {
        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(client.getTenantId())
                .client(client)
                .finalPrice(finalPrice)
                .build();
        return appointment;
    }
}
