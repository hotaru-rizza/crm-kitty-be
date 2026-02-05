package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.PaymentMethod;
import com.inkflow.crm.domain.enums.PaymentType;
import com.inkflow.crm.domain.enums.TransactionCategory;
import com.inkflow.crm.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private TransactionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    private PaymentType paymentType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_id")
    private Staff processedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(name = "date", nullable = false)
    private Instant date;

    @Column(name = "cash_amount", precision = 12, scale = 2)
    private BigDecimal cashAmount;

    @Column(name = "card_amount", precision = 12, scale = 2)
    private BigDecimal cardAmount;

    @Column(name = "tip_amount", precision = 10, scale = 2)
    private BigDecimal tipAmount;

    // Refund tracking
    @Column(name = "original_transaction_id")
    private UUID originalTransactionId;

    @Column(name = "refund_reason")
    private String refundReason;

    @Column(name = "refunded_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "is_refunded", nullable = false)
    @Builder.Default
    private Boolean isRefunded = false;

    // Receipt number for tracking
    @Column(name = "receipt_number", unique = true)
    private String receiptNumber;

    public boolean isIncome() {
        return type == TransactionType.INCOME;
    }

    public boolean isExpense() {
        return type == TransactionType.EXPENSE;
    }

    public boolean isServicePayment() {
        return category == TransactionCategory.SERVICE;
    }

    public boolean isRefund() {
        return paymentType == PaymentType.REFUND;
    }

    public boolean canBeRefunded() {
        if (isRefund() || isRefunded) return false;
        BigDecimal remainingAmount = amount.subtract(refundedAmount != null ? refundedAmount : BigDecimal.ZERO);
        return remainingAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal getRefundableAmount() {
        if (!canBeRefunded()) return BigDecimal.ZERO;
        return amount.subtract(refundedAmount != null ? refundedAmount : BigDecimal.ZERO);
    }

    public void addRefundedAmount(BigDecimal refundAmount) {
        if (this.refundedAmount == null) {
            this.refundedAmount = BigDecimal.ZERO;
        }
        this.refundedAmount = this.refundedAmount.add(refundAmount);
        if (this.refundedAmount.compareTo(this.amount) >= 0) {
            this.isRefunded = true;
        }
    }
}
