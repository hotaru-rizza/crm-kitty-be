package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "transaction_category_configs", indexes = {
    @Index(name = "idx_tcc_tenant", columnList = "tenant_id, deleted_at"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCategoryConfig extends BaseEntity {

    @Column(name = "category_key", nullable = false)
    private String categoryKey;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "color")
    private String color;

    /** INCOME, EXPENSE, NEUTRAL */
    @Column(name = "pl_type", nullable = false)
    private String plType;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;
}
