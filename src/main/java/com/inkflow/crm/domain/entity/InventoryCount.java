package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/** Інвентаризація — звірка фактичних залишків з системними */
@Entity
@Table(name = "inventory_counts", indexes = {
    @Index(name = "idx_inv_count_tenant", columnList = "tenant_id, deleted_at"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryCount extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    /** IN_PROGRESS → DONE */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "IN_PROGRESS";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @OneToMany(mappedBy = "count", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<InventoryCountItem> items = new ArrayList<>();
}
