package com.inkflow.crm.module.payment.support;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Transaction;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.payment.dto.AppointmentPaymentSummaryDto;
import com.inkflow.crm.module.payment.dto.PaymentDto;
import com.inkflow.crm.module.payment.mapper.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AppointmentPaymentSummaryCalculator {

    private final TransactionRepository transactionRepository;
    private final PaymentMapper paymentMapper;

    public AppointmentPaymentSummaryDto calculate(Appointment appointment) {
        List<Transaction> transactions = transactionRepository
                .findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointment.getId());

        PaymentTotals totals = accumulateTotals(transactions);
        BigDecimal finalPrice = resolveFinalPrice(appointment);
        BigDecimal remainingBalance = calculateRemainingBalance(finalPrice, totals.totalPaid());
        List<PaymentDto> payments = transactions.stream().map(paymentMapper::toDto).toList();

        return AppointmentPaymentSummaryDto.builder()
                .appointmentId(appointment.getId())
                .servicePrice(appointment.getPrice())
                .discount(appointment.getDiscount())
                .finalPrice(finalPrice)
                .totalPaid(totals.totalPaid())
                .depositPaid(totals.depositPaid())
                .servicePaid(totals.servicePaid())
                .totalRefunded(totals.totalRefunded())
                .totalTips(totals.totalTips())
                .remainingBalance(remainingBalance)
                .isFullyPaid(remainingBalance.compareTo(BigDecimal.ZERO) <= 0)
                .hasDeposit(totals.depositPaid().compareTo(BigDecimal.ZERO) > 0)
                .hasRefunds(totals.totalRefunded().compareTo(BigDecimal.ZERO) > 0)
                .payments(payments)
                .build();
    }

    public List<PaymentDto> listPayments(UUID appointmentId) {
        return transactionRepository.findByAppointmentIdAndDeletedAtIsNullOrderByDateDesc(appointmentId).stream()
                .map(paymentMapper::toDto)
                .toList();
    }

    private PaymentTotals accumulateTotals(List<Transaction> transactions) {
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal depositPaid = BigDecimal.ZERO;
        BigDecimal servicePaid = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;

        for (Transaction transaction : transactions) {
            if (transaction.getPaymentType() == null) {
                continue;
            }

            PaymentType paymentType = transaction.getPaymentType();
            BigDecimal amount = transaction.getAmount();

            switch (paymentType) {
                case DEPOSIT -> {
                    depositPaid = depositPaid.add(amount);
                    totalPaid = totalPaid.add(amount);
                }
                case SERVICE_PAYMENT -> {
                    servicePaid = servicePaid.add(amount);
                    totalPaid = totalPaid.add(amount);
                }
                case REFUND -> {
                    totalRefunded = totalRefunded.add(amount);
                    totalPaid = totalPaid.subtract(amount);
                }
                case TIP -> totalTips = totalTips.add(amount);
            }
        }

        return new PaymentTotals(totalPaid, depositPaid, servicePaid, totalRefunded, totalTips);
    }

    private BigDecimal resolveFinalPrice(Appointment appointment) {
        if (appointment.getFinalPrice() != null) {
            return appointment.getFinalPrice();
        }
        return appointment.getPrice().subtract(appointment.getDiscount());
    }

    private BigDecimal calculateRemainingBalance(BigDecimal finalPrice, BigDecimal totalPaid) {
        BigDecimal remainingBalance = finalPrice.subtract(totalPaid);
        if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return remainingBalance;
    }

    private record PaymentTotals(
            BigDecimal totalPaid,
            BigDecimal depositPaid,
            BigDecimal servicePaid,
            BigDecimal totalRefunded,
            BigDecimal totalTips
    ) {
    }
}
