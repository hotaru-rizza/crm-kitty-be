package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "supply_invoice_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private SupplyInvoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Double quantity;

    @Column(name = "cost_per_unit", precision = 10, scale = 2)
    private BigDecimal costPerUnit;
}
