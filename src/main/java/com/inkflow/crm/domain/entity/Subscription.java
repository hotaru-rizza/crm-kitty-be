package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;


    @Column(name = "plan", nullable = false)
    @Builder.Default
    private String plan = "TRIAL";


    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";


    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;


    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;


    @Column(name = "monthly_price", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal monthlyPrice = BigDecimal.valueOf(399);


    @Column(name = "last_invoice_id")
    private String lastInvoiceId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;


    public boolean isActive() {
        if ("CANCELLED".equals(status)) return false;
        if ("TRIAL".equals(plan)) {
            return trialEndsAt != null && Instant.now().isBefore(trialEndsAt);
        }

        return currentPeriodEnd != null && Instant.now().isBefore(currentPeriodEnd);
    }

    public long daysRemaining() {
        Instant end = "TRIAL".equals(plan) ? trialEndsAt : currentPeriodEnd;
        if (end == null) return 0;
        long seconds = end.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, seconds / 86400);
    }
}
