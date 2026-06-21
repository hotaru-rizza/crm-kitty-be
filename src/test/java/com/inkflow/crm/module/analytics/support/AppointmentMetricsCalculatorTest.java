package com.inkflow.crm.module.analytics.support;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppointmentMetricsCalculatorTest {

    private final AppointmentMetricsCalculator calculator = new AppointmentMetricsCalculator();

    @Test
    void sumDoneRevenue_countsOnlyCompletedAppointments() {
        Appointment done = appointment(AppointmentStatus.COMPLETED, BigDecimal.valueOf(1000));
        Appointment cancelled = appointment(AppointmentStatus.CANCELLED, BigDecimal.valueOf(500));

        BigDecimal revenue = calculator.sumDoneRevenue(List.of(done, cancelled));

        assertEquals(BigDecimal.valueOf(1000), revenue);
    }

    @Test
    void sumCostOfSales_sumsServiceCostForDoneAppointments() {
        Service service = Service.builder().costPrice(BigDecimal.valueOf(200)).build();
        Appointment done = appointment(AppointmentStatus.COMPLETED, BigDecimal.valueOf(1000));
        done.setService(service);

        BigDecimal cost = calculator.sumCostOfSales(List.of(done));

        assertEquals(BigDecimal.valueOf(200), cost);
    }

    @Test
    void calculateCompletionRate_returnsPercentage() {
        BigDecimal rate = calculator.calculateCompletionRate(3, 4);

        assertEquals(BigDecimal.valueOf(75.0), rate);
    }

    @Test
    void calculateCompletionRate_returnsZeroWhenNoAppointments() {
        assertEquals(BigDecimal.ZERO, calculator.calculateCompletionRate(0, 0));
    }

    @Test
    void hasArtistAndClient_detectsRelations() {
        Appointment withRelations = appointment(AppointmentStatus.SCHEDULED, BigDecimal.TEN);
        withRelations.setArtist(com.inkflow.crm.domain.entity.Staff.builder().id(java.util.UUID.randomUUID()).build());
        withRelations.setClient(com.inkflow.crm.domain.entity.Client.builder().id(java.util.UUID.randomUUID()).build());

        assertTrue(calculator.hasArtist(withRelations));
        assertTrue(calculator.hasClient(withRelations));
        assertFalse(calculator.isDone(withRelations));
    }

    @Test
    void shouldCountStatusesAcrossMixedAppointments() {
        List<Appointment> appointments = List.of(
                appointment(AppointmentStatus.COMPLETED, BigDecimal.TEN),
                appointment(AppointmentStatus.COMPLETED, BigDecimal.TEN),
                appointment(AppointmentStatus.CANCELLED, BigDecimal.TEN),
                appointment(AppointmentStatus.SCHEDULED, BigDecimal.TEN));

        assertEquals(4, calculator.countTotal(appointments));
        assertEquals(2, calculator.countCompleted(appointments));
        assertEquals(1, calculator.countCancelled(appointments));
        assertEquals(2, calculator.countByStatus(appointments, AppointmentStatus.COMPLETED));
    }

    @Test
    void shouldCalculateAvgCheckWhenCompletedPresent() {
        BigDecimal avgCheck = calculator.calculateAvgCheck(BigDecimal.valueOf(1000), 3);

        assertEquals(new BigDecimal("333.33"), avgCheck);
    }

    @Test
    void shouldReturnZeroAvgCheckWhenNoCompletedAppointments() {
        assertEquals(BigDecimal.ZERO, calculator.calculateAvgCheck(BigDecimal.valueOf(1000), 0));
    }

    @Test
    void shouldCalculateRepeatRateWithOneDecimalRounding() {
        assertEquals(40.0, calculator.calculateRepeatRate(2, 5));
    }

    @Test
    void shouldReturnZeroRepeatRateWhenNoUniqueClients() {
        assertEquals(0, calculator.calculateRepeatRate(0, 0));
    }

    @Test
    void shouldCalculateUtilizationRateAsPercentage() {
        assertEquals(75.0, calculator.calculateUtilizationRate(6, 8));
    }

    @Test
    void shouldCapUtilizationRateAtOneHundred() {
        assertEquals(100.0, calculator.calculateUtilizationRate(12, 8));
    }

    @Test
    void shouldReturnZeroUtilizationWhenNoScheduledHours() {
        assertEquals(0, calculator.calculateUtilizationRate(5, 0));
    }

    @Test
    void shouldRoundToOneDecimal() {
        assertEquals(3.5, calculator.roundOneDecimal(3.456));
        assertEquals(3.4, calculator.roundOneDecimal(3.44));
    }

    @Test
    void shouldCalculateToPercent() {
        assertEquals(new BigDecimal("12.5000"), calculator.toPercent(BigDecimal.valueOf(25), BigDecimal.valueOf(200)));
    }

    @Test
    void shouldReturnZeroToPercentWhenWholeIsZero() {
        assertEquals(BigDecimal.ZERO, calculator.toPercent(BigDecimal.TEN, BigDecimal.ZERO));
    }

    @Test
    void shouldTreatNullFinalPriceAsZeroInRevenue() {
        Appointment doneWithoutPrice = appointment(AppointmentStatus.COMPLETED, null);
        Appointment doneWithPrice = appointment(AppointmentStatus.COMPLETED, BigDecimal.valueOf(400));

        assertEquals(BigDecimal.valueOf(400), calculator.sumDoneRevenue(List.of(doneWithoutPrice, doneWithPrice)));
    }

    @Test
    void shouldSkipCostWhenServiceOrCostPriceMissing() {
        Appointment withoutService = appointment(AppointmentStatus.COMPLETED, BigDecimal.TEN);
        Appointment withoutCost = appointment(AppointmentStatus.COMPLETED, BigDecimal.TEN);
        withoutCost.setService(Service.builder().costPrice(null).build());
        Appointment withCost = appointment(AppointmentStatus.COMPLETED, BigDecimal.TEN);
        withCost.setService(Service.builder().costPrice(BigDecimal.valueOf(150)).build());

        assertEquals(BigDecimal.valueOf(150), calculator.sumCostOfSales(List.of(withoutService, withoutCost, withCost)));
    }

    @Test
    void shouldDetectActiveCancelledAndServicePresence() {
        Appointment cancelled = appointment(AppointmentStatus.CANCELLED, BigDecimal.TEN);
        Appointment active = appointment(AppointmentStatus.SCHEDULED, BigDecimal.TEN);
        active.setService(Service.builder().build());

        assertTrue(calculator.isCancelled(cancelled));
        assertFalse(calculator.isActive(cancelled));
        assertTrue(calculator.isActive(active));
        assertTrue(calculator.hasService(active));
        assertFalse(calculator.hasService(cancelled));
    }

    private Appointment appointment(AppointmentStatus status, BigDecimal finalPrice) {
        return Appointment.builder()
                .status(status)
                .finalPrice(finalPrice)
                .prepayment(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .build();
    }
}
