package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Товарна накладна — групова поставка від постачальника */
@Entity
@Table(name = "supply_invoices", indexes = {
    @Index(name = "idx_supply_invoice_tenant", columnList = "tenant_id, deleted_at"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SupplyInvoice extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "supplier_name")
    private String supplierName;

    @Column(name = "note")
    private String note;

    /** DRAFT → CONFIRMED */
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SupplyInvoiceItem> items = new ArrayList<>();
}
