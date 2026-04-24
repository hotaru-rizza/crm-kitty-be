package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "warehouses", indexes = {
    @Index(name = "idx_warehouse_tenant", columnList = "tenant_id, deleted_at"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Warehouse extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "notes")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff assignedStaff;
}
