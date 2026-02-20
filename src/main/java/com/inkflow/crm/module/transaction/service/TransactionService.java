package com.inkflow.crm.module.transaction.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.enums.*;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.transaction.dto.*;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final LocationRepository locationRepository;

    @Transactional(readOnly = true)
    public List<TransactionDto> getAllTransactions(PageRequest pageRequest, String type, String category, Instant from, Instant to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Transaction> page = resolveTransactionPage(pageRequest, type, category, from, to, tenantId);
        return page.getContent().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaginationDto getPagination(PageRequest pageRequest, String type, String category, Instant from, Instant to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Transaction> page = resolveTransactionPage(pageRequest, type, category, from, to, tenantId);
        return PaginationDto.from(page);
    }

    private Page<Transaction> resolveTransactionPage(PageRequest pageRequest, String type, String category, Instant from, Instant to, UUID tenantId) {
        boolean hasDateRange = from != null && to != null;
        if (type != null && hasDateRange) {
            return transactionRepository.findByTenantIdAndTypeAndDateRangeAndDeletedAtIsNull(
                    tenantId, TransactionType.fromValue(type), from, to, pageRequest.toPageable());
        } else if (type != null) {
            return transactionRepository.findByTenantIdAndTypeAndDeletedAtIsNull(
                    tenantId, TransactionType.fromValue(type), pageRequest.toPageable());
        } else if (category != null) {
            return transactionRepository.findByTenantIdAndCategoryAndDeletedAtIsNull(
                    tenantId, TransactionCategory.fromValue(category), pageRequest.toPageable());
        } else if (hasDateRange) {
            return transactionRepository.findByTenantIdAndDateRangeAndDeletedAtIsNull(
                    tenantId, from, to, pageRequest.toPageable());
        } else {
            return transactionRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageRequest.toPageable());
        }
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransactionById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Transaction transaction = transactionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.transaction(id.toString()));
        return mapToDto(transaction);
    }

    @Transactional
    public TransactionDto createTransaction(CreateTransactionRequest request) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Location location = locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getLocationId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.location(request.getLocationId().toString()));

        Appointment appointment = null;
        if (request.getAppointmentId() != null) {
            appointment = appointmentRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getAppointmentId(), tenantId)
                    .orElseThrow(() -> ResourceNotFoundException.appointment(request.getAppointmentId().toString()));
        }

        Staff staff = null;
        if (request.getStaffId() != null) {
            staff = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getStaffId(), tenantId)
                    .orElseThrow(() -> ResourceNotFoundException.staff(request.getStaffId().toString()));
        }

        Transaction transaction = Transaction.builder()
                .tenantId(tenantId)
                .type(TransactionType.fromValue(request.getType()))
                .category(TransactionCategory.fromValue(request.getCategory()))
                .amount(request.getAmount())
                .paymentMethod(PaymentMethod.fromValue(request.getPaymentMethod()))
                .description(request.getDescription())
                .appointment(appointment)
                .staff(staff)
                .location(location)
                .date(request.getDate())
                .cashAmount(request.getCashAmount())
                .cardAmount(request.getCardAmount())
                .tipAmount(request.getTipAmount())
                .build();

        transaction = transactionRepository.save(transaction);
        return mapToDto(transaction);
    }

    @Transactional
    public void deleteTransaction(UUID id) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Transaction transaction = transactionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.transaction(id.toString()));
        transaction.softDelete();
        transactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public FinanceStatsDto getFinanceStats(Instant from, Instant to, UUID staffId) {
        SecurityUtils.requireAdminAccess();
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        BigDecimal totalIncome = transactionRepository.sumByTypeAndDateRange(tenantId, TransactionType.INCOME, from, to);
        BigDecimal totalExpenses = transactionRepository.sumByTypeAndDateRange(tenantId, TransactionType.EXPENSE, from, to);

        if (totalIncome == null) totalIncome = BigDecimal.ZERO;
        if (totalExpenses == null) totalExpenses = BigDecimal.ZERO;

        Map<String, BigDecimal> byCategory = new HashMap<>();
        List<Object[]> categoryResults = transactionRepository.sumByCategoryAndDateRange(tenantId, from, to);
        for (Object[] row : categoryResults) {
            TransactionCategory category = (TransactionCategory) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            byCategory.put(category.getValue(), amount);
        }

        Map<String, BigDecimal> byPaymentMethod = new HashMap<>();
        List<Object[]> paymentResults = transactionRepository.sumByPaymentMethodAndDateRange(tenantId, from, to);
        for (Object[] row : paymentResults) {
            PaymentMethod method = (PaymentMethod) row[0];
            BigDecimal amount = (BigDecimal) row[1];
            byPaymentMethod.put(method.getValue(), amount);
        }

        List<FinanceStatsDto.ArtistRevenueDto> byArtist = new ArrayList<>();
        List<Object[]> artistResults = staffId != null
                ? transactionRepository.sumByArtistAndDateRangeForStaff(tenantId, staffId, from, to)
                : transactionRepository.sumByArtistAndDateRange(tenantId, from, to);
        for (Object[] row : artistResults) {
            UUID artistId = (UUID) row[0];
            String firstName = (String) row[1];
            String lastName = (String) row[2];
            BigDecimal revenue = (BigDecimal) row[3];
            Long count = (Long) row[4];
            String calendarColor = (String) row[5];

            byArtist.add(FinanceStatsDto.ArtistRevenueDto.builder()
                    .artistId(artistId.toString())
                    .artistName(firstName + " " + lastName)
                    .revenue(revenue)
                    .appointmentsCount(count.intValue())
                    .calendarColor(calendarColor)
                    .build());
        }

        List<Object[]> dateResults = staffId != null
                ? transactionRepository.sumIncomeByDayAndDateRangeForStaff(tenantId, staffId, from, to)
                : transactionRepository.sumIncomeByDayAndDateRange(tenantId, from, to);
        Map<String, BigDecimal> byDate = new java.util.LinkedHashMap<>();
        for (Object[] row : dateResults) {
            String day = row[0].toString();
            BigDecimal amount = new BigDecimal(row[1].toString());
            byDate.put(day, amount);
        }

        return FinanceStatsDto.builder()
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .netProfit(totalIncome.subtract(totalExpenses))
                .byCategory(byCategory)
                .byPaymentMethod(byPaymentMethod)
                .byArtist(byArtist)
                .byDate(byDate)
                .build();
    }

    private TransactionDto mapToDto(Transaction transaction) {
        return TransactionDto.builder()
                .id(transaction.getId())
                .type(transaction.getType().getValue())
                .category(transaction.getCategory().getValue())
                .amount(transaction.getAmount())
                .paymentMethod(transaction.getPaymentMethod().getValue())
                .description(transaction.getDescription())
                .appointmentId(transaction.getAppointment() != null ? transaction.getAppointment().getId() : null)
                .staffId(transaction.getStaff() != null ? transaction.getStaff().getId() : null)
                .staffName(transaction.getStaff() != null ? transaction.getStaff().getFullName() : null)
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
