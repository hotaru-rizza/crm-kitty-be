package com.inkflow.crm.module.analytics.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.TransactionCategoryConfig;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.TransactionCategory;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.TransactionCategoryConfigRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.analytics.dto.PnlDto;
import com.inkflow.crm.module.analytics.support.AppointmentMetricsCalculator;
import com.inkflow.crm.module.analytics.support.CommissionCalculator;
import com.inkflow.crm.domain.enums.SalaryType;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PnlAnalyticsServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionCategoryConfigRepository categoryConfigRepository;

    @Mock
    private CommissionCalculator commissionCalculator;

    @Mock
    private AppointmentMetricsCalculator metrics;

    @InjectMocks
    private PnlAnalyticsService pnlAnalyticsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getPnl_calculatesProfitFromDoneAppointments() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        UUID artistId = UUID.randomUUID();
        Appointment done = Appointment.builder()
                .tenantId(tenantId)
                .status(AppointmentStatus.COMPLETED)
                .finalPrice(BigDecimal.valueOf(1000))
                .artist(Staff.builder().id(artistId).firstName("Alex").lastName("Ink").build())
                .build();

        when(appointmentRepository.findByDateRange(from, to, null)).thenReturn(List.of(done));
        when(metrics.sumDoneRevenue(List.of(done))).thenReturn(BigDecimal.valueOf(1000));
        when(metrics.sumCostOfSales(List.of(done))).thenReturn(BigDecimal.valueOf(200));
        when(metrics.roundOneDecimal(80.0)).thenReturn(80.0);
        when(metrics.hasArtist(done)).thenReturn(true);
        when(metrics.toPercent(any(), any())).thenReturn(BigDecimal.valueOf(80));
        when(commissionCalculator.resolveSalaryType(any())).thenReturn(SalaryType.PERCENT);
        when(commissionCalculator.calculate(any(), any())).thenReturn(BigDecimal.valueOf(100));
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.EXPENSE, from, to, null))
                .thenReturn(BigDecimal.valueOf(50));
        when(categoryConfigRepository.findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc())
                .thenReturn(List.of());
        when(transactionRepository.sumByCategoryAndDateRange(from, to, null)).thenReturn(List.of());

        PnlDto pnl = pnlAnalyticsService.getPnl(from, to);

        assertEquals(BigDecimal.valueOf(1000), pnl.getRevenue());
        assertEquals(BigDecimal.valueOf(200), pnl.getCostOfSales());
        assertEquals(BigDecimal.valueOf(800), pnl.getGrossProfit());
        assertEquals(BigDecimal.valueOf(100), pnl.getStaffCommissions());
        assertEquals(BigDecimal.valueOf(50), pnl.getOtherExpenses());
        assertEquals(BigDecimal.valueOf(650), pnl.getNetProfit());
        assertEquals(80.0, pnl.getGrossMargin());
    }

    @Test
    void shouldReturnZeroExpensesWhenRepositoryReturnsNull() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        when(appointmentRepository.findByDateRange(from, to, null)).thenReturn(List.of());
        when(metrics.sumDoneRevenue(List.of())).thenReturn(BigDecimal.ZERO);
        when(metrics.sumCostOfSales(List.of())).thenReturn(BigDecimal.ZERO);
        when(metrics.roundOneDecimal(0.0)).thenReturn(0.0);
        when(metrics.toPercent(any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.EXPENSE, from, to, null))
                .thenReturn(null);
        when(categoryConfigRepository.findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc())
                .thenReturn(List.of());
        when(transactionRepository.sumByCategoryAndDateRange(from, to, null)).thenReturn(List.of());

        PnlDto pnl = pnlAnalyticsService.getPnl(from, to);

        assertEquals(BigDecimal.ZERO, pnl.getOtherExpenses());
        assertEquals(BigDecimal.ZERO, pnl.getNetProfit());
    }

    @Test
    void shouldExcludeAppointmentsWithoutArtistFromStaffBreakdown() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        Appointment withoutArtist = Appointment.builder()
                .tenantId(tenantId)
                .status(AppointmentStatus.COMPLETED)
                .finalPrice(BigDecimal.valueOf(500))
                .build();

        when(appointmentRepository.findByDateRange(from, to, null))
                .thenReturn(List.of(withoutArtist));
        when(metrics.sumDoneRevenue(List.of(withoutArtist))).thenReturn(BigDecimal.valueOf(500));
        when(metrics.sumCostOfSales(List.of(withoutArtist))).thenReturn(BigDecimal.ZERO);
        when(metrics.roundOneDecimal(any(Double.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(metrics.toPercent(any(), any())).thenReturn(BigDecimal.valueOf(100));
        when(metrics.hasArtist(withoutArtist)).thenReturn(false);
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.EXPENSE, from, to, null))
                .thenReturn(BigDecimal.ZERO);
        when(categoryConfigRepository.findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc())
                .thenReturn(List.of());
        when(transactionRepository.sumByCategoryAndDateRange(from, to, null)).thenReturn(List.of());

        PnlDto pnl = pnlAnalyticsService.getPnl(from, to);

        assertTrue(pnl.getStaffBreakdown().isEmpty());
        assertEquals(BigDecimal.ZERO, pnl.getStaffCommissions());
    }

    @Test
    void shouldIncludeOnlyExpenseCategoryLinesInBreakdown() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        when(appointmentRepository.findByDateRange(from, to, null)).thenReturn(List.of());
        when(metrics.sumDoneRevenue(List.of())).thenReturn(BigDecimal.ZERO);
        when(metrics.sumCostOfSales(List.of())).thenReturn(BigDecimal.ZERO);
        when(metrics.roundOneDecimal(0.0)).thenReturn(0.0);
        when(metrics.toPercent(any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.sumByTypeAndDateRange(TransactionType.EXPENSE, from, to, null))
                .thenReturn(BigDecimal.valueOf(120));
        when(categoryConfigRepository.findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc())
                .thenReturn(List.of(
                        TransactionCategoryConfig.builder()
                                .categoryKey(TransactionCategory.RENT.getValue())
                                .label("Rent")
                                .color("#ff0000")
                                .plType("EXPENSE")
                                .build(),
                        TransactionCategoryConfig.builder()
                                .categoryKey(TransactionCategory.SERVICE.getValue())
                                .label("Service income")
                                .plType("REVENUE")
                                .build()
                ));
        when(transactionRepository.sumByCategoryAndDateRange(from, to, null)).thenReturn(List.of(
                new Object[]{"rent", BigDecimal.valueOf(120)},
                new Object[]{"service", BigDecimal.valueOf(900)}
        ));

        PnlDto pnl = pnlAnalyticsService.getPnl(from, to);

        assertEquals(1, pnl.getExpenseBreakdown().size());
        assertEquals("rent", pnl.getExpenseBreakdown().getFirst().getCategoryKey());
        assertEquals("Rent", pnl.getExpenseBreakdown().getFirst().getLabel());
        assertEquals(BigDecimal.valueOf(120), pnl.getExpenseBreakdown().getFirst().getAmount());
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
