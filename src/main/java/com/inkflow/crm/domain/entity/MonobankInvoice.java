package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "monobank_invoices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MonobankInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;


    @Column(name = "appointment_id")
    private UUID appointmentId;


    @Column(name = "invoice_type", nullable = false)
    @Builder.Default
    private String invoiceType = "APPOINTMENT";


    @Column(name = "monobank_invoice_id", nullable = false, unique = true)
    private String monobankInvoiceId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;


    @Column(name = "ccy")
    @Builder.Default
    private Integer ccy = 980;

    @Column(name = "page_url", nullable = false, length = 512)
    private String pageUrl;


    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "pending";


    @Column(name = "payment_type", nullable = false)
    private String paymentType;


    @Column(name = "transaction_id")
    private UUID transactionId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
