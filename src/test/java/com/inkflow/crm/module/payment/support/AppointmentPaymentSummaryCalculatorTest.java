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

    private Transaction transaction(UUID appointmentId, PaymentType paymentType, BigDecimal amount) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .type(TransactionType.INCOME)
                .category(TransactionCategory.SERVICE)
                .paymentType(paymentType)
                .paymentMethod(PaymentMethod.CASH)
                .amount(amount)
                .build();
    }
}
