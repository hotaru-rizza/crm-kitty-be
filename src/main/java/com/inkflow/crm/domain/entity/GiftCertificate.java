package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "gift_certificates", indexes = {
    @Index(name = "idx_gc_tenant_deleted", columnList = "tenant_id, deleted_at"),
    @Index(name = "idx_gc_code", columnList = "code", unique = true),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class GiftCertificate extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "initial_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal initialAmount;

    @Column(name = "remaining_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal remainingAmount;

    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "buyer_phone")
    private String buyerPhone;

    @Column(name = "holder_name")
    private String holderName;

    @Column(name = "holder_phone")
    private String holderPhone;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /** ACTIVE, USED, EXPIRED, CANCELLED */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isUsable() {
        if (!"ACTIVE".equals(status)) return false;
        if (expiresAt != null && LocalDate.now().isAfter(expiresAt)) return false;
        return remainingAmount != null && remainingAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
