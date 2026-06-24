package com.inkflow.crm.module.payment.support;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.PaymentMethod;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.domain.enums.TransactionCategory;
import com.inkflow.crm.domain.enums.TransactionType;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.payment.dto.AppointmentPaymentSummaryDto;
import com.inkflow.crm.module.payment.dto.PaymentDto;
import com.inkflow.crm.module.payment.mapper.PaymentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentPaymentSummaryCalculatorTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private AppointmentPaymentSummaryCalculator calculator;

    @Test
    void calculate_computesRemainingBalanceAfterDeposit() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .price(BigDecimal.valueOf(1000))
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(1000))
                .prepayment(BigDecimal.ZERO)
                .build();

        Transaction deposit = transaction(appointmentId, PaymentType.DEPOSIT, BigDecimal.valueOf(300));
        when(transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointmentId))
                .thenReturn(List.of(deposit));
        when(paymentMapper.toDto(deposit)).thenReturn(PaymentDto.builder().amount(deposit.getAmount()).build());

        AppointmentPaymentSummaryDto summary = calculator.calculate(appointment);

        assertEquals(BigDecimal.valueOf(300), summary.getDepositPaid());
        assertEquals(BigDecimal.valueOf(700), summary.getRemainingBalance());
        assertFalse(summary.getIsFullyPaid());
        assertTrue(summary.getHasDeposit());
    }

    @Test
    void shouldSubtractRefundsFromTotalPaidAndFlagHasRefunds() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointmentWithFinalPrice(appointmentId, BigDecimal.valueOf(1000));

        Transaction deposit = transaction(appointmentId, PaymentType.DEPOSIT, BigDecimal.valueOf(300));
        Transaction refund = transaction(appointmentId, PaymentType.REFUND, BigDecimal.valueOf(100));
        when(transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointmentId))
                .thenReturn(List.of(refund, deposit));
        when(paymentMapper.toDto(deposit)).thenReturn(PaymentDto.builder().amount(deposit.getAmount()).build());
        when(paymentMapper.toDto(refund)).thenReturn(PaymentDto.builder().amount(refund.getAmount()).build());

        AppointmentPaymentSummaryDto summary = calculator.calculate(appointment);

        assertEquals(BigDecimal.valueOf(300), summary.getDepositPaid());
        assertEquals(BigDecimal.valueOf(100), summary.getTotalRefunded());
        assertEquals(BigDecimal.valueOf(200), summary.getTotalPaid());
        assertEquals(BigDecimal.valueOf(800), summary.getRemainingBalance());
        assertTrue(summary.getHasRefunds());
        assertFalse(summary.getIsFullyPaid());
    }

    @Test
    void shouldTrackTipsSeparatelyFromServicePayments() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointmentWithFinalPrice(appointmentId, BigDecimal.valueOf(500));

        Transaction servicePayment = transaction(appointmentId, PaymentType.SERVICE_PAYMENT, BigDecimal.valueOf(500));
        Transaction tip = transaction(appointmentId, PaymentType.TIP, BigDecimal.valueOf(50));
        when(transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointmentId))
                .thenReturn(List.of(tip, servicePayment));
        when(paymentMapper.toDto(servicePayment)).thenReturn(PaymentDto.builder().amount(servicePayment.getAmount()).build());
        when(paymentMapper.toDto(tip)).thenReturn(PaymentDto.builder().amount(tip.getAmount()).build());

        AppointmentPaymentSummaryDto summary = calculator.calculate(appointment);

        assertEquals(BigDecimal.valueOf(500), summary.getServicePaid());
        assertEquals(BigDecimal.valueOf(50), summary.getTotalTips());
        assertEquals(BigDecimal.valueOf(500), summary.getTotalPaid());
        assertTrue(summary.getIsFullyPaid());
    }

    @Test
    void shouldSkipTransactionsWithNullPaymentType() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointmentWithFinalPrice(appointmentId, BigDecimal.valueOf(400));

        Transaction ignored = transaction(appointmentId, null, BigDecimal.valueOf(999));
        Transaction payment = transaction(appointmentId, PaymentType.SERVICE_PAYMENT, BigDecimal.valueOf(400));
        when(transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointmentId))
                .thenReturn(List.of(ignored, payment));
        when(paymentMapper.toDto(ignored)).thenReturn(PaymentDto.builder().amount(ignored.getAmount()).build());
        when(paymentMapper.toDto(payment)).thenReturn(PaymentDto.builder().amount(payment.getAmount()).build());

        AppointmentPaymentSummaryDto summary = calculator.calculate(appointment);

        assertEquals(BigDecimal.valueOf(400), summary.getTotalPaid());
        assertEquals(BigDecimal.ZERO, summary.getRemainingBalance());
    }

    @Test
    void shouldDeriveFinalPriceWhenNullOnAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .price(BigDecimal.valueOf(800))
                .discount(BigDecimal.valueOf(100))
                .prepayment(BigDecimal.ZERO)
                .build();

        when(transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointmentId))
                .thenReturn(List.of());

        AppointmentPaymentSummaryDto summary = calculator.calculate(appointment);

        assertEquals(BigDecimal.valueOf(700), summary.getFinalPrice());
        assertEquals(BigDecimal.valueOf(700), summary.getRemainingBalance());
    }

    @Test
    void shouldClampRemainingBalanceToZeroWhenOverpaid() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = appointmentWithFinalPrice(appointmentId, BigDecimal.valueOf(500));

        Transaction overpayment = transaction(appointmentId, PaymentType.SERVICE_PAYMENT, BigDecimal.valueOf(600));
        when(transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointmentId))
                .thenReturn(List.of(overpayment));
        when(paymentMapper.toDto(overpayment)).thenReturn(PaymentDto.builder().amount(overpayment.getAmount()).build());

        AppointmentPaymentSummaryDto summary = calculator.calculate(appointment);

        assertEquals(BigDecimal.ZERO, summary.getRemainingBalance());
        assertTrue(summary.getIsFullyPaid());
    }

    @Test
    void shouldListPaymentsViaMapper() {
        UUID appointmentId = UUID.randomUUID();
        Transaction payment = transaction(appointmentId, PaymentType.SERVICE_PAYMENT, BigDecimal.valueOf(120));
        PaymentDto dto = PaymentDto.builder().id(payment.getId()).amount(payment.getAmount()).build();

        when(transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointmentId))
                .thenReturn(List.of(payment));
        when(paymentMapper.toDto(payment)).thenReturn(dto);

        List<PaymentDto> payments = calculator.listPayments(appointmentId);

        assertEquals(1, payments.size());
        assertEquals(dto, payments.getFirst());
        verify(paymentMapper).toDto(payment);
    }

    @Test
    void calculate_marksFullyPaidWhenBalanceZero() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .price(BigDecimal.valueOf(500))
                .discount(BigDecimal.ZERO)
                .finalPrice(BigDecimal.valueOf(500))
                .prepayment(BigDecimal.ZERO)
                .build();

        Transaction payment = transaction(appointmentId, PaymentType.SERVICE_PAYMENT, BigDecimal.valueOf(500));
        when(transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointmentId))
                .thenReturn(List.of(payment));
        when(paymentMapper.toDto(payment)).thenReturn(PaymentDto.builder().amount(payment.getAmount()).build());

        AppointmentPaymentSummaryDto summary = calculator.calculate(appointment);

        assertTrue(summary.getIsFullyPaid());
        assertEquals(BigDecimal.ZERO, summary.getRemainingBalance());
    }

    private Appointment appointmentWithFinalPrice(UUID appointmentId, BigDecimal finalPrice) {
        return Appointment.builder()
                .id(appointmentId)
                .price(finalPrice)
                .discount(BigDecimal.ZERO)
                .finalPrice(finalPrice)
                .prepayment(BigDecimal.ZERO)
                .build();
    }

    private Transaction transaction(UUID appointmentId, PaymentType paymentType, BigDecimal amount) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .type(TransactionType.INCOME)
                .category(TransactionCategory.SERVICE.getValue())
                .paymentType(paymentType)
                .paymentMethod(PaymentMethod.CASH)
                .amount(amount)
                .build();
    }
}
