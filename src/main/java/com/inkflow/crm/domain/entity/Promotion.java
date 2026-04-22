package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.DiscountType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "promotions", indexes = {
    @Index(name = "idx_promotion_tenant_deleted", columnList = "tenant_id, deleted_at"),
    @Index(name = "idx_promotion_active", columnList = "is_active"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promotion_services", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "service_id")
    @Builder.Default
    private List<java.util.UUID> serviceIds = new ArrayList<>();

    public boolean isValidOn(LocalDate date) {
        if (!Boolean.TRUE.equals(isActive)) return false;
        if (validFrom != null && date.isBefore(validFrom)) return false;
        if (validTo != null && date.isAfter(validTo)) return false;
        return true;
    }

    public BigDecimal applyTo(BigDecimal price) {
        if (discountType == DiscountType.PERCENT) {
            BigDecimal factor = BigDecimal.ONE.subtract(
                discountValue.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
            return price.multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP);
        } else {
            BigDecimal result = price.subtract(discountValue);
            return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
        }
    }
}
