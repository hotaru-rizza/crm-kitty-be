package com.inkflow.crm.module.transaction.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.transaction.dto.*;
import com.inkflow.crm.module.transaction.service.TransactionService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    @RequirePermission("finance.view")
    public ResponseEntity<ApiResponse<List<TransactionDto>>> getAllTransactions(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<TransactionDto> transactions = transactionService.getAllTransactions(pageRequest, type, category, from, to);
        PaginationDto pagination = transactionService.getPagination(pageRequest, type, category, from, to);
        return ResponseEntity.ok(ApiResponse.success(transactions, pagination));
    }

    @GetMapping("/{id}")
    @RequirePermission("finance.view")
    public ResponseEntity<ApiResponse<TransactionDto>> getTransaction(@PathVariable UUID id) {
        TransactionDto transaction = transactionService.getTransactionById(id);
        return ResponseEntity.ok(ApiResponse.success(transaction));
    }

    /**
     * Get finance stats. Supports either:
     * - period: "month", "week", "year" (calculates from/to automatically)
     * - from/to: explicit date range
     */
    @GetMapping("/stats")
    @RequirePermission("finance.view")
    public ResponseEntity<ApiResponse<FinanceStatsDto>> getFinanceStats(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) UUID staffId) {

        LocalDate now = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();

        if (period != null) {
            switch (period.toLowerCase()) {
                case "week" -> {
                    from = now.minusWeeks(1).atStartOfDay(zone).toInstant();
                    to = now.plusDays(1).atStartOfDay(zone).toInstant();
                }
                case "month" -> {
                    from = now.withDayOfMonth(1).atStartOfDay(zone).toInstant();
                    to = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant();
                }
                case "year" -> {
                    from = now.withDayOfYear(1).atStartOfDay(zone).toInstant();
                    to = now.plusYears(1).withDayOfYear(1).atStartOfDay(zone).toInstant();
                }
                default -> {
                    from = now.withDayOfMonth(1).atStartOfDay(zone).toInstant();
                    to = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant();
                }
            }
        } else if (from == null || to == null) {
            from = now.withDayOfMonth(1).atStartOfDay(zone).toInstant();
            to = now.plusMonths(1).withDayOfMonth(1).atStartOfDay(zone).toInstant();
        }

        FinanceStatsDto stats = transactionService.getFinanceStats(from, to, staffId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @PostMapping
    @RequirePermission("finance.create")
    public ResponseEntity<ApiResponse<TransactionDto>> createTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionDto transaction = transactionService.createTransaction(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(transaction));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTransaction(@PathVariable UUID id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }
}
