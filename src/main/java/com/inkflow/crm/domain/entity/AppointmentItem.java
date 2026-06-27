package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.AppointmentItemSource;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "appointment_items", indexes = {
        @Index(name = "idx_appointment_item_appointment", columnList = "appointment_id"),
        @Index(name = "idx_appointment_item_tenant", columnList = "tenant_id"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private AppointmentItemSource source;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private Integer durationMinutes = 0;

    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    public void recalculateLineTotal() {
        int qty = quantity != null && quantity > 0 ? quantity : 1;
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        lineTotal = price.multiply(BigDecimal.valueOf(qty));
    }
}
