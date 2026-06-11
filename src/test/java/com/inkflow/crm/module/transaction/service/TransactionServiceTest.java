package com.inkflow.crm.module.transaction.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.PaymentMethod;
import com.inkflow.crm.domain.enums.TransactionCategory;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.transaction.dto.CreateTransactionRequest;
import com.inkflow.crm.module.transaction.dto.FinanceStatsDto;
import com.inkflow.crm.module.transaction.dto.TransactionDto;
import com.inkflow.crm.module.transaction.mapper.TransactionMapper;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @InjectMocks
    private TransactionService transactionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTransaction_persistsIncomeTransaction() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticate(tenantId);

        Location location = Location.builder().id(locationId).tenantId(tenantId).build();
        Transaction saved = Transaction.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .category(TransactionCategory.SERVICE)
                .amount(BigDecimal.valueOf(1500))
                .location(location)
                .build();

        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .locationId(locationId)
                .type("income")
                .category("service")
                .amount(BigDecimal.valueOf(1500))
                .paymentMethod("cash")
                .date(Instant.now())
                .description("Session payment")
                .build();

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.of(location));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(saved);
        when(transactionMapper.toDto(saved)).thenReturn(TransactionDto.builder().id(saved.getId()).amount(saved.getAmount()).build());

        TransactionDto result = transactionService.createTransaction(request);

        assertEquals(BigDecimal.valueOf(1500), result.getAmount());
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void createTransaction_rejectsMissingLocation() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticate(tenantId);

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.empty());

        CreateTransactionRequest request = CreateTransactionRequest.builder()
                .locationId(locationId)
                .type("income")
                .category("service")
                .amount(BigDecimal.TEN)
                .paymentMethod("cash")
                .date(Instant.now())
                .build();

        assertThrows(ResourceNotFoundException.class, () -> transactionService.createTransaction(request));
    }

    @Test
    void deleteTransaction_softDeletesWhenOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        authenticate(tenantId);

        Transaction transaction = Transaction.builder()
                .id(transactionId)
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .amount(BigDecimal.TEN)
                .paymentMethod(PaymentMethod.CASH)
                .build();

        when(transactionRepository.findByIdAndTenantIdAndDeletedAtIsNull(transactionId, tenantId))
                .thenReturn(Optional.of(transaction));
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        transactionService.deleteTransaction(transactionId);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void deleteTransaction_rejectsNonOwner() {
        UUID tenantId = UUID.randomUUID();
        authenticateAsArtist(tenantId);

        assertThrows(AccessDeniedException.class, () -> transactionService.deleteTransaction(UUID.randomUUID()));
    }

    @Test
    void getFinanceStats_calculatesNetProfit() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");

        when(transactionRepository.sumByTypeAndDateRange(tenantId, TransactionType.INCOME, from, to))
                .thenReturn(BigDecimal.valueOf(5000));
        when(transactionRepository.sumByTypeAndDateRange(tenantId, TransactionType.EXPENSE, from, to))
                .thenReturn(BigDecimal.valueOf(1200));
        when(transactionRepository.sumByCategoryAndDateRange(tenantId, from, to)).thenReturn(Collections.emptyList());
        when(transactionRepository.sumByPaymentMethodAndDateRange(tenantId, from, to)).thenReturn(Collections.emptyList());
        when(transactionRepository.sumByArtistAndDateRange(tenantId, from, to)).thenReturn(Collections.emptyList());
        when(transactionRepository.sumIncomeByDayAndDateRange(tenantId, from, to)).thenReturn(Collections.emptyList());

        FinanceStatsDto stats = transactionService.getFinanceStats(from, to, null);

        assertEquals(BigDecimal.valueOf(5000), stats.getTotalIncome());
        assertEquals(BigDecimal.valueOf(1200), stats.getTotalExpenses());
        assertEquals(BigDecimal.valueOf(3800), stats.getNetProfit());
    }

    @Test
    void getAllTransactions_usesStaffIdFilterWhenStaffIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId);

        PageRequest pageRequest = new PageRequest();
        Page<Transaction> emptyPage = new PageImpl<>(List.of());

        when(transactionRepository.findByTenantIdAndStaffIdAndDeletedAtIsNull(
                tenantId, staffId, pageRequest.toPageable())).thenReturn(emptyPage);

        transactionService.getAllTransactions(pageRequest, null, null, null, null, staffId);

        verify(transactionRepository).findByTenantIdAndStaffIdAndDeletedAtIsNull(
                tenantId, staffId, pageRequest.toPageable());
    }

    @Test
    void getAllTransactions_usesStaffIdAndDateRangeWhenBothProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");
        PageRequest pageRequest = new PageRequest();
        Page<Transaction> emptyPage = new PageImpl<>(List.of());

        when(transactionRepository.findByTenantIdAndStaffIdAndDateRangeAndDeletedAtIsNull(
                tenantId, staffId, from, to, pageRequest.toPageable())).thenReturn(emptyPage);

        transactionService.getAllTransactions(pageRequest, null, null, from, to, staffId);

        verify(transactionRepository).findByTenantIdAndStaffIdAndDateRangeAndDeletedAtIsNull(
                tenantId, staffId, from, to, pageRequest.toPageable());
    }

    @Test
    void getAllTransactions_usesDateRangeFilterWhenFromAndToProvided() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");
        PageRequest pageRequest = new PageRequest();
        Page<Transaction> emptyPage = new PageImpl<>(List.of());

        when(transactionRepository.findByTenantIdAndDateRangeAndDeletedAtIsNull(
                tenantId, from, to, pageRequest.toPageable())).thenReturn(emptyPage);

        transactionService.getAllTransactions(pageRequest, null, null, from, to, null);

        verify(transactionRepository).findByTenantIdAndDateRangeAndDeletedAtIsNull(
                tenantId, from, to, pageRequest.toPageable());
    }

    @Test
    void getTransactionById_rejectsForeignTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        authenticate(tenantId);

        when(transactionRepository.findByIdAndTenantIdAndDeletedAtIsNull(transactionId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.getTransactionById(transactionId));
    }

    private void authenticateAsArtist(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.ARTIST)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
