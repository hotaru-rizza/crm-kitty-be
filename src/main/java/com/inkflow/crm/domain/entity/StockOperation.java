package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/** ARRIVAL = надходження, WRITEOFF = списання, ADJUSTMENT = ручне коригування */
@Entity
@Table(name = "stock_operations", indexes = {
    @Index(name = "idx_stock_op_tenant", columnList = "tenant_id, deleted_at"),
    @Index(name = "idx_stock_op_product", columnList = "product_id"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class StockOperation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "quantity", nullable = false)
    private Double quantity;

    @Column(name = "cost_per_unit", precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    @Column(name = "note")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;
}
