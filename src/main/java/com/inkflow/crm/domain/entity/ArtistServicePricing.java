package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "artist_service_pricing",
       uniqueConstraints = @UniqueConstraint(columnNames = {"staff_id", "service_id"}),
       indexes = {
           @Index(name = "idx_asp_staff", columnList = "staff_id"),
           @Index(name = "idx_asp_service", columnList = "service_id"),
       })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistServicePricing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "duration")
    private Integer duration;
}
