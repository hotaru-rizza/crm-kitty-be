package com.inkflow.crm.module.client.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.ClientBalanceEntry;
import com.inkflow.crm.domain.enums.ClientBalanceReason;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientBalanceEntryRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.module.client.mapper.ClientBalanceMapper;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientBalanceServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientBalanceEntryRepository balanceEntryRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ClientBalanceMapper clientBalanceMapper;

    @InjectMocks
    private ClientBalanceService clientBalanceService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void record_shouldAdjustClientBalanceWhenPaymentReceived() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        Client client = client(tenantId, BigDecimal.ZERO);
        when(clientRepository.save(client)).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceEntryRepository.save(any(ClientBalanceEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        clientBalanceService.record(client, BigDecimal.valueOf(200), ClientBalanceReason.PAYMENT, null, null, null);

        assertEquals(BigDecimal.valueOf(200), client.getBalance());

        ArgumentCaptor<ClientBalanceEntry> entryCaptor = ArgumentCaptor.forClass(ClientBalanceEntry.class);
        verify(balanceEntryRepository).save(entryCaptor.capture());
        assertEquals(ClientBalanceReason.PAYMENT, entryCaptor.getValue().getReason());
    }

    @Test
    void chargeAppointmentOnCompletion_shouldCreateDebtWhenUnpaid() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        Client client = client(tenantId, BigDecimal.ZERO);
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .client(client)
                .finalPrice(BigDecimal.valueOf(500))
                .build();

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));
        when(clientRepository.save(client)).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.save(appointment)).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceEntryRepository.save(any(ClientBalanceEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        clientBalanceService.chargeAppointmentOnCompletion(appointmentId, tenantId);

        assertEquals(BigDecimal.valueOf(-500), client.getBalance());
        assertNotNull(appointment.getBalanceChargedAt());
    }

    @Test
    void chargeAppointmentOnCompletion_shouldBeIdempotent() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();

        Client client = client(tenantId, BigDecimal.ZERO);
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .client(client)
                .finalPrice(BigDecimal.valueOf(500))
                .balanceChargedAt(java.time.Instant.now())
                .build();

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));

        clientBalanceService.chargeAppointmentOnCompletion(appointmentId, tenantId);

        assertEquals(BigDecimal.ZERO, client.getBalance());
    }

    @Test
    void reverseAppointmentCharge_shouldRestoreBalanceAfterRestore() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID());

        Client client = client(tenantId, BigDecimal.valueOf(-500));
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .client(client)
                .finalPrice(BigDecimal.valueOf(500))
                .balanceChargedAt(java.time.Instant.now())
                .build();

        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));
        when(clientRepository.save(client)).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentRepository.save(appointment)).thenAnswer(invocation -> invocation.getArgument(0));
        when(balanceEntryRepository.save(any(ClientBalanceEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        clientBalanceService.reverseAppointmentCharge(appointmentId, tenantId);

        assertEquals(BigDecimal.ZERO, client.getBalance());
        assertNull(appointment.getBalanceChargedAt());
    }

    @Test
    void validateBalanceSpend_shouldRejectWhenBalanceIsZero() {
        Client client = client(UUID.randomUUID(), BigDecimal.ZERO);

        assertThrows(BusinessRuleException.class,
                () -> clientBalanceService.validateBalanceSpend(client, BigDecimal.TEN));
    }

    @Test
    void validateBalanceSpend_shouldRejectWhenClientHasDebt() {
        Client client = client(UUID.randomUUID(), BigDecimal.valueOf(-500));

        assertThrows(BusinessRuleException.class,
                () -> clientBalanceService.validateBalanceSpend(client, BigDecimal.valueOf(200)));
    }

    @Test
    void validateBalanceSpend_shouldRejectWhenCreditInsufficient() {
        Client client = client(UUID.randomUUID(), BigDecimal.valueOf(50));

        assertThrows(BusinessRuleException.class,
                () -> clientBalanceService.validateBalanceSpend(client, BigDecimal.valueOf(100)));
    }

    private Client client(UUID tenantId, BigDecimal balance) {
        return Client.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName("Test")
                .lastName("Client")
                .email("test@example.com")
                .balance(balance)
                .build();
    }

    private void authenticate(UUID tenantId, UUID userId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
