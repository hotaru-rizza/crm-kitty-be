package com.inkflow.crm.module.transaction.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
import static org.mockito.Mockito.never;
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

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        transactionService.getAllTransactions(pageRequest, null, null, null, null, List.of(staffId), null, null, null);

        verify(transactionRepository).findAll(any(Specification.class), eq(pageRequest.toPageable()));
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

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        transactionService.getAllTransactions(pageRequest, null, null, from, to, List.of(staffId), null, null, null);

        verify(transactionRepository).findAll(any(Specification.class), eq(pageRequest.toPageable()));
    }

    @Test
    void getAllTransactions_usesDateRangeFilterWhenFromAndToProvided() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");
        PageRequest pageRequest = new PageRequest();
        Page<Transaction> emptyPage = new PageImpl<>(List.of());

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        transactionService.getAllTransactions(pageRequest, null, null, from, to, null, null, null, null);

        verify(transactionRepository).findAll(any(Specification.class), eq(pageRequest.toPageable()));
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

    @Test
    void shouldReturnTransactionDtoWhenFoundById() {
        UUID tenantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        authenticate(tenantId);

        Transaction transaction = Transaction.builder()
                .id(transactionId)
                .tenantId(tenantId)
                .type(TransactionType.INCOME)
                .amount(BigDecimal.valueOf(250))
                .build();
        TransactionDto dto = TransactionDto.builder().id(transactionId).amount(BigDecimal.valueOf(250)).build();

        when(transactionRepository.findByIdAndTenantIdAndDeletedAtIsNull(transactionId, tenantId))
                .thenReturn(Optional.of(transaction));
        when(transactionMapper.toDto(transaction)).thenReturn(dto);

        TransactionDto result = transactionService.getTransactionById(transactionId);

        assertEquals(transactionId, result.getId());
        assertEquals(BigDecimal.valueOf(250), result.getAmount());
    }

    @Test
    void shouldUseTypeFilterWhenTypeProvided() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        PageRequest pageRequest = new PageRequest();
        Page<Transaction> emptyPage = new PageImpl<>(List.of());

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        transactionService.getAllTransactions(pageRequest, "expense", null, null, null, null, null, null, null);

        verify(transactionRepository).findAll(any(Specification.class), eq(pageRequest.toPageable()));
    }

    @Test
    void shouldUseTypeAndDateRangeWhenBothProvided() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");
        PageRequest pageRequest = new PageRequest();
        Page<Transaction> emptyPage = new PageImpl<>(List.of());

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        transactionService.getAllTransactions(pageRequest, "income", null, from, to, null, null, null, null);

        verify(transactionRepository).findAll(any(Specification.class), eq(pageRequest.toPageable()));
    }

    @Test
    void shouldUseCategoryFilterWhenCategoryProvided() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        PageRequest pageRequest = new PageRequest();
        Page<Transaction> emptyPage = new PageImpl<>(List.of());

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        transactionService.getAllTransactions(pageRequest, null, "rent", null, null, null, null, null, null);

        verify(transactionRepository).findAll(any(Specification.class), eq(pageRequest.toPageable()));
    }

    @Test
    void shouldUseDefaultQueryWhenNoFiltersProvided() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        PageRequest pageRequest = new PageRequest();
        Page<Transaction> emptyPage = new PageImpl<>(List.of());

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        transactionService.getAllTransactions(pageRequest, null, null, null, null, null, null, null, null);

        verify(transactionRepository).findAll(any(Specification.class), eq(pageRequest.toPageable()));
    }

    @Test
    void shouldMapTransactionsToDtosWhenListing() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder().id(transactionId).tenantId(tenantId).build();
        TransactionDto dto = TransactionDto.builder().id(transactionId).build();
        PageRequest pageRequest = new PageRequest();
        Page<Transaction> page = new PageImpl<>(List.of(transaction));

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(transactionMapper.toDto(transaction)).thenReturn(dto);

        PageResult<TransactionDto> result = transactionService.getAllTransactions(
                pageRequest, null, null, null, null, null, null, null, null);

        assertEquals(1, result.getData().size());
        assertEquals(transactionId, result.getData().getFirst().getId());
        verify(transactionMapper).toDto(transaction);
    }

    @Test
    void shouldDefaultNullTotalsToZeroWhenRepositoryReturnsNull() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");

        when(transactionRepository.sumByTypeAndDateRange(tenantId, TransactionType.INCOME, from, to))
                .thenReturn(null);
        when(transactionRepository.sumByTypeAndDateRange(tenantId, TransactionType.EXPENSE, from, to))
                .thenReturn(null);
        when(transactionRepository.sumByCategoryAndDateRange(tenantId, from, to)).thenReturn(Collections.emptyList());
        when(transactionRepository.sumByPaymentMethodAndDateRange(tenantId, from, to)).thenReturn(Collections.emptyList());
        when(transactionRepository.sumByArtistAndDateRange(tenantId, from, to)).thenReturn(Collections.emptyList());
        when(transactionRepository.sumIncomeByDayAndDateRange(tenantId, from, to)).thenReturn(Collections.emptyList());

        FinanceStatsDto stats = transactionService.getFinanceStats(from, to, null);

        assertEquals(BigDecimal.ZERO, stats.getTotalIncome());
        assertEquals(BigDecimal.ZERO, stats.getTotalExpenses());
        assertEquals(BigDecimal.ZERO, stats.getNetProfit());
    }

    @Test
    void shouldPopulateBreakdownsWhenStatsDataPresent() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");
        UUID artistId = UUID.randomUUID();

        when(transactionRepository.sumByTypeAndDateRange(tenantId, TransactionType.INCOME, from, to))
                .thenReturn(BigDecimal.valueOf(3000));
        when(transactionRepository.sumByTypeAndDateRange(tenantId, TransactionType.EXPENSE, from, to))
                .thenReturn(BigDecimal.valueOf(500));
        when(transactionRepository.sumByCategoryAndDateRange(tenantId, from, to))
                .thenReturn(List.<Object[]>of(new Object[]{TransactionCategory.SERVICE, BigDecimal.valueOf(2800)}));
        when(transactionRepository.sumByPaymentMethodAndDateRange(tenantId, from, to))
                .thenReturn(List.<Object[]>of(new Object[]{PaymentMethod.CARD, BigDecimal.valueOf(2000)}));
        when(transactionRepository.sumByArtistAndDateRange(tenantId, from, to))
                .thenReturn(List.<Object[]>of(new Object[]{
                        artistId, "Jane", "Doe", BigDecimal.valueOf(2800), 4L, "#aabbcc"
                }));
        when(transactionRepository.sumIncomeByDayAndDateRange(tenantId, from, to))
                .thenReturn(List.<Object[]>of(new Object[]{"2025-01-10", BigDecimal.valueOf(1500)}));

        FinanceStatsDto stats = transactionService.getFinanceStats(from, to, null);

        assertEquals(BigDecimal.valueOf(2500), stats.getNetProfit());
        assertEquals(BigDecimal.valueOf(2800), stats.getByCategory().get("service"));
        assertEquals(BigDecimal.valueOf(2000), stats.getByPaymentMethod().get("card"));
        assertEquals(1, stats.getByArtist().size());
        assertEquals(artistId.toString(), stats.getByArtist().getFirst().getArtistId());
        assertEquals("Jane Doe", stats.getByArtist().getFirst().getArtistName());
        assertEquals(BigDecimal.valueOf(2800), stats.getByArtist().getFirst().getRevenue());
        assertEquals(4, stats.getByArtist().getFirst().getAppointmentsCount());
        assertEquals("#aabbcc", stats.getByArtist().getFirst().getCalendarColor());
        assertEquals(BigDecimal.valueOf(1500), stats.getByDate().get("2025-01-10"));
    }

    @Test
    void shouldUseStaffScopedQueriesWhenStaffIdProvidedForStats() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");

        when(transactionRepository.sumByTypeAndDateRange(tenantId, TransactionType.INCOME, from, to))
                .thenReturn(BigDecimal.valueOf(1000));
        when(transactionRepository.sumByTypeAndDateRange(tenantId, TransactionType.EXPENSE, from, to))
                .thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByCategoryAndDateRange(tenantId, from, to)).thenReturn(Collections.emptyList());
        when(transactionRepository.sumByPaymentMethodAndDateRange(tenantId, from, to)).thenReturn(Collections.emptyList());
        when(transactionRepository.sumByArtistAndDateRangeForStaff(tenantId, staffId, from, to))
                .thenReturn(List.<Object[]>of(new Object[]{
                        staffId, "Alex", "Smith", BigDecimal.valueOf(1000), 2L, "#112233"
                }));
        when(transactionRepository.sumIncomeByDayAndDateRangeForStaff(tenantId, staffId, from, to))
                .thenReturn(List.<Object[]>of(new Object[]{"2025-01-05", BigDecimal.valueOf(600)}));

        FinanceStatsDto stats = transactionService.getFinanceStats(from, to, staffId);

        assertEquals(BigDecimal.valueOf(1000), stats.getTotalIncome());
        assertEquals(staffId.toString(), stats.getByArtist().getFirst().getArtistId());
        assertEquals(BigDecimal.valueOf(600), stats.getByDate().get("2025-01-05"));
        verify(transactionRepository).sumByArtistAndDateRangeForStaff(tenantId, staffId, from, to);
        verify(transactionRepository, never()).sumByArtistAndDateRange(tenantId, from, to);
        verify(transactionRepository).sumIncomeByDayAndDateRangeForStaff(tenantId, staffId, from, to);
        verify(transactionRepository, never()).sumIncomeByDayAndDateRange(tenantId, from, to);
    }

    @Test
    void shouldThrowNotFoundWhenTransactionMissingOnDelete() {
        UUID tenantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        authenticate(tenantId);

        when(transactionRepository.findByIdAndTenantIdAndDeletedAtIsNull(transactionId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> transactionService.deleteTransaction(transactionId));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void shouldLinkAppointmentWhenAppointmentIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId);

        Location location = Location.builder().id(locationId).tenantId(tenantId).build();
        Appointment appointment = Appointment.builder().id(appointmentId).tenantId(tenantId).build();
        CreateTransactionRequest request = baseCreateRequest(locationId)
                .appointmentId(appointmentId)
                .build();

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.of(location));
        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.of(appointment));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toDto(any(Transaction.class))).thenReturn(TransactionDto.builder().build());

        transactionService.createTransaction(request);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(appointment, captor.getValue().getAppointment());
    }

    @Test
    void shouldRejectMissingAppointmentWhenAppointmentIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        authenticate(tenantId);

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.of(Location.builder().id(locationId).tenantId(tenantId).build()));
        when(appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(appointmentId, tenantId))
                .thenReturn(Optional.empty());

        CreateTransactionRequest request = baseCreateRequest(locationId)
                .appointmentId(appointmentId)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> transactionService.createTransaction(request));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void shouldLinkStaffWhenStaffIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId);

        Location location = Location.builder().id(locationId).tenantId(tenantId).build();
        Staff staff = Staff.builder().id(staffId).tenantId(tenantId).build();
        CreateTransactionRequest request = baseCreateRequest(locationId)
                .staffId(staffId)
                .build();

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.of(location));
        when(staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId))
                .thenReturn(Optional.of(staff));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toDto(any(Transaction.class))).thenReturn(TransactionDto.builder().build());

        transactionService.createTransaction(request);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(staff, captor.getValue().getStaff());
    }

    @Test
    void shouldRejectMissingStaffWhenStaffIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId);

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.of(Location.builder().id(locationId).tenantId(tenantId).build()));
        when(staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(staffId, tenantId))
                .thenReturn(Optional.empty());

        CreateTransactionRequest request = baseCreateRequest(locationId)
                .staffId(staffId)
                .build();

        assertThrows(ResourceNotFoundException.class, () -> transactionService.createTransaction(request));
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void shouldScopeLocationToTenantWhenCreatingTransaction() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticate(tenantId);

        Location location = Location.builder().id(locationId).tenantId(tenantId).build();
        CreateTransactionRequest request = baseCreateRequest(locationId).build();

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.of(location));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.toDto(any(Transaction.class))).thenReturn(TransactionDto.builder().build());

        transactionService.createTransaction(request);

        verify(locationRepository).findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId);
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(location, captor.getValue().getLocation());
        assertEquals(tenantId, captor.getValue().getTenantId());
    }

    @Test
    void shouldCombineAllFiltersWhenListing() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2025-01-01T00:00:00Z");
        Instant to = Instant.parse("2025-01-31T23:59:59Z");
        PageRequest pageRequest = new PageRequest();
        Page<Transaction> emptyPage = new PageImpl<>(List.of());

        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        transactionService.getAllTransactions(
                pageRequest, "income", "service", from, to, List.of(staffId), "card", BigDecimal.TEN, BigDecimal.valueOf(100));

        verify(transactionRepository).findAll(any(Specification.class), eq(pageRequest.toPageable()));
    }

    private CreateTransactionRequest.CreateTransactionRequestBuilder baseCreateRequest(UUID locationId) {
        return CreateTransactionRequest.builder()
                .locationId(locationId)
                .type("income")
                .category("service")
                .amount(BigDecimal.valueOf(100))
                .paymentMethod("cash")
                .date(Instant.parse("2025-01-15T12:00:00Z"));
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
