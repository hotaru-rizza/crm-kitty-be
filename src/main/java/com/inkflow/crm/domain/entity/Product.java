package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_tenant", columnList = "tenant_id, deleted_at"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Product extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "category")
    private String category;

    @Column(name = "sku")
    private String sku;

    @Column(name = "description")
    private String description;

    /** пcs, ml, g, boxes, etc. */
    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "cost_per_unit", precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    @Column(name = "stock_quantity", nullable = false)
    @Builder.Default
    private Double stockQuantity = 0.0;

    @Column(name = "min_stock_level")
    private Double minStockLevel;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
