package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.RequestSource;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "clients",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "phone"}),
       indexes = {
           @Index(name = "idx_client_tenant_deleted", columnList = "tenant_id, deleted_at"),
           @Index(name = "idx_client_phone_tenant", columnList = "phone, tenant_id"),
           @Index(name = "idx_client_location", columnList = "location_id"),
       })
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Client extends BaseEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "instagram")
    private String instagram;

    @Column(name = "telegram")
    private String telegram;

    @Column(name = "whatsapp")
    private String whatsapp;

    @Column(name = "facebook")
    private String facebook;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "client_tags", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "tag")
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "client_medical_conditions", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "condition")
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private List<String> medicalConditions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private RequestSource source;

    @Column(name = "dormant", nullable = false)
    @Builder.Default
    private boolean dormant = false;

    @Column(name = "blacklisted", nullable = false)
    @Builder.Default
    private boolean blacklisted = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_visit")
    private Instant lastVisit;

    @Column(name = "first_visit")
    private Instant firstVisit;

    @Column(name = "balance", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "total_visits", nullable = false)
    @Builder.Default
    private Integer totalVisits = 0;

    @Column(name = "cancelled_visits", nullable = false)
    @Builder.Default
    private Integer cancelledVisits = 0;

    @Column(name = "ltv", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal ltv = BigDecimal.ZERO;

    @OneToMany(mappedBy = "client")
    @Builder.Default
    private List<Project> projects = new ArrayList<>();

    @OneToMany(mappedBy = "client")
    @Builder.Default
    private List<Appointment> appointments = new ArrayList<>();

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean hasMedicalConditions() {
        return medicalConditions != null && !medicalConditions.isEmpty();
    }

    public void incrementVisits() {
        this.totalVisits++;
        this.lastVisit = Instant.now();
    }

    public void decrementVisits() {
        if (this.totalVisits > 0) {
            this.totalVisits--;
        }
    }

    public void incrementCancelledVisits() {
        this.cancelledVisits++;
    }

    public void decrementCancelledVisits() {
        if (this.cancelledVisits > 0) {
            this.cancelledVisits--;
        }
    }

    public void addToLtv(BigDecimal amount) {
        this.ltv = this.ltv.add(amount);
    }

    public void adjustBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}
