package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.ClientBalanceReason;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "client_balance_entries", indexes = {
        @Index(name = "idx_client_balance_entries_client", columnList = "tenant_id, client_id, created_at"),
        @Index(name = "idx_client_balance_entries_appointment", columnList = "tenant_id, appointment_id"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ClientBalanceEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private ClientBalanceReason reason;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
