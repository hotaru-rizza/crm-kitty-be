package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.converter.SupportedLocaleConverter;
import com.inkflow.crm.domain.enums.AccountType;
import com.inkflow.crm.domain.enums.SupportedCurrency;
import com.inkflow.crm.domain.enums.SupportedLocale;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "logo_url")
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private SupportedCurrency currency = SupportedCurrency.UAH;

    @Column(name = "timezone", nullable = false)
    private String timezone = "Europe/Kyiv";

    @Column(name = "language", nullable = false)
    @Convert(converter = SupportedLocaleConverter.class)
    private SupportedLocale language = SupportedLocale.UK;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType = AccountType.STUDIO;

    @Column(name = "company_size")
    private String companySize;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "client_dormancy_days", nullable = false)
    @Builder.Default
    private Integer clientDormancyDays = 90;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
