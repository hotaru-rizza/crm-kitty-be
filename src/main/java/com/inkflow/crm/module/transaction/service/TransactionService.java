package com.inkflow.crm.module.transaction.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.enums.*;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.transaction.dto.*;
import com.inkflow.crm.module.transaction.mapper.TransactionMapper;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final LocationRepository locationRepository;
    private final TransactionMapper transactionMapper;

    @Transactional(readOnly = true)
    public PageResult<TransactionDto> getAllTransactions(
            PageRequest pageRequest,
            String type,
            String category,
            Instant from,
            Instant to,
            List<UUID> staffIds,
            String paymentMethod,
            BigDecimal amountMin,
            BigDecimal amountMax
    ) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Specification<Transaction> spec = TransactionSpecifications.filtered(
                tenantId, type, category, from, to, staffIds, paymentMethod, amountMin, amountMax);
        Page<Transaction> page = transactionRepository.findAll(spec, pageRequest.toPageable());
        List<TransactionDto> data = page.getContent().stream().map(transactionMapper::toDto).toList();
        return new PageResult<>(data, PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransactionById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Transaction transaction = transactionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.transaction(id.toString()));
        return transactionMapper.toDto(transaction);
    }

    @Transactional
    public TransactionDto createTransaction(CreateTransactionRequest request) {
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
        log.info("Transaction created: tenantId={} transactionId={}", tenantId, transaction.getId());
        return transactionMapper.toDto(transaction);
    }

    @Transactional
    public void deleteTransaction(UUID id) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Transaction transaction = transactionRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.transaction(id.toString()));
        transaction.softDelete();
        transactionRepository.save(transaction);
        log.info("Transaction deleted: tenantId={} transactionId={}", tenantId, id);
    }

    @Transactional(readOnly = true)
    public FinanceStatsDto getFinanceStats(Instant from, Instant to, UUID staffId) {
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
}
