package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "appointments", indexes = {
    @Index(name = "idx_appointment_tenant_start", columnList = "tenant_id, start_time"),
    @Index(name = "idx_appointment_tenant_deleted", columnList = "tenant_id, deleted_at"),
    @Index(name = "idx_appointment_artist", columnList = "artist_id"),
    @Index(name = "idx_appointment_client", columnList = "client_id"),
    @Index(name = "idx_appointment_service", columnList = "service_id"),
    @Index(name = "idx_appointment_location", columnList = "location_id"),
    @Index(name = "idx_appointment_status", columnList = "status"),
    @Index(name = "idx_appointment_start_time", columnList = "start_time"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private Staff artist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status = AppointmentStatus.NEW;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "prepayment", nullable = false, precision = 10, scale = 2)
    private BigDecimal prepayment = BigDecimal.ZERO;

    @Column(name = "discount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(name = "final_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal finalPrice;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "sketch_image")
    private String sketchImage;

    @Column(name = "waiver_signed", nullable = false)
    private Boolean waiverSigned = false;

    @Column(name = "consent_token", unique = true)
    private String consentToken;

    @Column(name = "google_event_id")
    private String googleEventId;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GalleryPhoto> photos = new ArrayList<>();

    @OneToMany(mappedBy = "appointment")
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @OneToOne(mappedBy = "appointment")
    private SignedWaiver signedWaiver;

    public void calculateFinalPrice() {
        this.finalPrice = this.price.subtract(this.discount);
    }

    public BigDecimal getAmountToPay() {
        return finalPrice.subtract(prepayment);
    }

    public boolean isActive() {
        return status.isActive();
    }

    public boolean canTransitionTo(AppointmentStatus newStatus) {
        return status.canTransitionTo(newStatus);
    }

    public void cancel(String reason) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = Instant.now();
    }

    public void markAsDone() {
        this.status = AppointmentStatus.DONE;
    }

    public int getDurationMinutes() {
        return (int) java.time.Duration.between(startTime, endTime).toMinutes();
    }
}
