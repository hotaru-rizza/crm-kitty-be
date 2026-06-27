package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.SalaryType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.analytics.support.AppointmentMetricsCalculator;
import com.inkflow.crm.module.analytics.support.CommissionCalculator;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffEarningsServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();

    @Mock
    private StaffLookup staffLookup;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private InkflowProperties inkflowProperties;

    private final AppointmentMetricsCalculator metricsCalculator = new AppointmentMetricsCalculator();
    private final CommissionCalculator commissionCalculator = new CommissionCalculator();

    private StaffEarningsService staffEarningsService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UserPrincipal.builder()
                                .id(STAFF_ID)
                                .tenantId(TENANT_ID)
                                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                                .build(),
                        null,
                        List.of()));
        when(inkflowProperties.defaultZoneId()).thenReturn(java.time.ZoneId.of("Europe/Kyiv"));

        staffEarningsService = new StaffEarningsService(
                staffLookup,
                appointmentRepository,
                metricsCalculator,
                commissionCalculator,
                inkflowProperties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCalculatePercentEarningsFromCompletedRevenue() {
        Staff staff = Staff.builder()
                .id(STAFF_ID)
                .salaryType(SalaryType.PERCENT)
                .salaryRate(BigDecimal.valueOf(20))
                .build();

        Appointment completed = Appointment.builder()
                .status(AppointmentStatus.COMPLETED)
                .finalPrice(BigDecimal.valueOf(1000))
                .build();

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-01T00:00:00Z");

        when(staffLookup.requireStaff(STAFF_ID)).thenReturn(staff);
        when(appointmentRepository.findByTenantIdAndArtistIdAndDateRange(TENANT_ID, STAFF_ID, from, to))
                .thenReturn(List.of(completed));

        var result = staffEarningsService.getEarnings(STAFF_ID, from, to);

        assertEquals(new BigDecimal("1000"), result.getRevenue());
        assertEquals(1, result.getAppointmentsCount());
        assertEquals("percent", result.getSalaryType());
        assertEquals(new BigDecimal("200.00"), result.getEarnings());
    }
}
