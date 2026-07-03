package com.inkflow.crm.module.transaction.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.*;
import com.inkflow.crm.domain.enums.*;
import com.inkflow.crm.domain.repository.*;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.finance.service.CategoryConfigService;
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
import java.math.RoundingMode;
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
    private final CategoryConfigService categoryConfigService;
    private final AuditRecorder auditRecorder;

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
        Specification<Transaction> spec = TransactionSpecifications.filtered( type, category, from, to, staffIds, paymentMethod, amountMin, amountMax);
        Page<Transaction> page = transactionRepository.findAll(spec, pageRequest.toPageable());
        List<TransactionDto> data = page.getContent().stream().map(transactionMapper::toDto).toList();
        return new PageResult<>(data, PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransactionById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Transaction transaction = transactionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.transaction(id.toString()));
        return transactionMapper.toDto(transaction);
    }

    @Transactional
    public TransactionDto createTransaction(CreateTransactionRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Location location = locationRepository.findByIdAndDeletedAtIsNull(request.getLocationId())
                .orElseThrow(() -> ResourceNotFoundException.location(request.getLocationId().toString()));

        Appointment appointment = null;
        if (request.getAppointmentId() != null) {
            appointment = appointmentRepository.findByIdAndDeletedAtIsNull(request.getAppointmentId())
                    .orElseThrow(() -> ResourceNotFoundException.appointment(request.getAppointmentId().toString()));
        }

        Staff staff = null;
        if (request.getStaffId() != null) {
            staff = staffRepository.findByIdAndDeletedAtIsNull(request.getStaffId())
                    .orElseThrow(() -> ResourceNotFoundException.staff(request.getStaffId().toString()));
        }

        TransactionType transactionType = TransactionType.fromValue(request.getType());
        String categoryKey = request.getCategory().trim().toLowerCase(Locale.ROOT);
        categoryConfigService.requireActiveCategoryForTransaction(tenantId, categoryKey, transactionType);

        Transaction transaction = Transaction.builder()
                .tenantId(tenantId)
                .type(transactionType)
                .category(categoryKey)
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
        auditTransactionCreated(transaction);
        return transactionMapper.toDto(transaction);
    }

    @Transactional
    public void deleteTransaction(UUID id) {
        SecurityUtils.requireOwner();
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Transaction transaction = transactionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.transaction(id.toString()));
        transaction.softDelete();
        transactionRepository.save(transaction);
        log.info("Transaction deleted: tenantId={} transactionId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.DELETE,
                AuditEntityType.TRANSACTION,
                id.toString(),
                formatTransactionLabel(transaction)
        );
    }

    @Transactional(readOnly = true)
    public FinanceStatsDto getFinanceStats(Instant from, Instant to, List<UUID> staffIds) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<UUID> staffFilter = staffIds != null ? staffIds : List.of();
        boolean filterByStaff = !staffFilter.isEmpty();

        BigDecimal totalIncome = nullToZero(filterByStaff
                ? transactionRepository.sumByTypeAndDateRangeForStaffs( TransactionType.INCOME, from, to, staffFilter)
                : transactionRepository.sumByTypeAndDateRange( TransactionType.INCOME, from, to));
        BigDecimal totalExpenses = nullToZero(filterByStaff
                ? transactionRepository.sumByTypeAndDateRangeForStaffs( TransactionType.EXPENSE, from, to, staffFilter)
                : transactionRepository.sumByTypeAndDateRange( TransactionType.EXPENSE, from, to));

        Map<String, BigDecimal> byCategory = new HashMap<>();
        for (Object[] row : filterByStaff
                ? transactionRepository.sumByCategoryAndDateRangeForStaffs( from, to, staffFilter)
                : transactionRepository.sumByCategoryAndDateRange( from, to)) {
            byCategory.put((String) row[0], (BigDecimal) row[1]);
        }

        Map<String, BigDecimal> byPaymentMethod = new HashMap<>();
        for (Object[] row : filterByStaff
                ? transactionRepository.sumByPaymentMethodAndDateRangeForStaffs( from, to, staffFilter)
                : transactionRepository.sumByPaymentMethodAndDateRange( from, to)) {
            byPaymentMethod.put(((PaymentMethod) row[0]).getValue(), (BigDecimal) row[1]);
        }

        List<FinanceStatsDto.ArtistRevenueDto> byArtist = new ArrayList<>();
        for (Object[] row : filterByStaff
                ? transactionRepository.sumByArtistAndDateRangeForStaffs( from, to, staffFilter)
                : transactionRepository.sumByArtistAndDateRange( from, to)) {
            byArtist.add(FinanceStatsDto.ArtistRevenueDto.builder()
                    .artistId(((UUID) row[0]).toString())
                    .artistName(row[1] + " " + row[2])
                    .revenue((BigDecimal) row[3])
                    .appointmentsCount(((Long) row[4]).intValue())
                    .calendarColor((String) row[5])
                    .build());
        }

        List<Object[]> dateRows = filterByStaff
                ? transactionRepository.sumIncomeByDayAndDateRangeForStaffs(tenantId, staffFilter, from, to)
                : transactionRepository.sumIncomeByDayAndDateRange(tenantId, from, to);
        Map<String, BigDecimal> byDate = new java.util.LinkedHashMap<>();
        for (Object[] row : dateRows) {
            byDate.put(row[0].toString(), new BigDecimal(row[1].toString()));
        }

        long incomeCount = filterByStaff
                ? transactionRepository.countByTypeAndDateRangeForStaffs( TransactionType.INCOME, from, to, staffFilter)
                : transactionRepository.countByTypeAndDateRange( TransactionType.INCOME, from, to);

        return FinanceStatsDto.builder()
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .netProfit(totalIncome.subtract(totalExpenses))
                .avgCheck(calculateAvgCheck(totalIncome, incomeCount))
                .incomeCount(incomeCount > 0 ? incomeCount : null)
                .byCategory(byCategory)
                .byPaymentMethod(byPaymentMethod)
                .byArtist(byArtist)
                .byDate(byDate)
                .build();
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static BigDecimal calculateAvgCheck(BigDecimal totalIncome, long incomeCount) {
        if (incomeCount <= 0 || totalIncome == null || totalIncome.signum() <= 0) {
            return null;
        }

        return totalIncome.divide(BigDecimal.valueOf(incomeCount), 2, RoundingMode.HALF_UP);
    }

    private void auditTransactionCreated(Transaction transaction) {
        AuditAction action = transaction.getType() == TransactionType.INCOME
                ? AuditAction.TXN_INCOME
                : AuditAction.TXN_EXPENSE;
        UUID clientId = transaction.getAppointment() != null && transaction.getAppointment().getClient() != null
                ? transaction.getAppointment().getClient().getId()
                : null;
        String details = transaction.getAmount() + " ₴ · " + transaction.getPaymentMethod().getValue();

        auditRecorder.record(
                action,
                AuditEntityType.TRANSACTION,
                transaction.getId().toString(),
                formatTransactionLabel(transaction),
                clientId,
                details
        );
    }

    private String formatTransactionLabel(Transaction transaction) {
        return transaction.getCategory() + " · " + transaction.getAmount() + " ₴";
    }
}
