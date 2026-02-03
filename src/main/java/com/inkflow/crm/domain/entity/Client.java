package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.ClientStatus;
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
       uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "phone"}))
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

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "instagram")
    private String instagram;

    @Column(name = "telegram")
    private String telegram;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "client_tags", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "client_medical_conditions", joinColumns = @JoinColumn(name = "client_id"))
    @Column(name = "condition")
    @Builder.Default
    private List<String> medicalConditions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    private RequestSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClientStatus status = ClientStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_visit")
    private Instant lastVisit;

    @Column(name = "total_visits", nullable = false)
    private Integer totalVisits = 0;

    @Column(name = "cancelled_visits", nullable = false)
    private Integer cancelledVisits = 0;

    @Column(name = "ltv", nullable = false, precision = 12, scale = 2)
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

    public void incrementCancelledVisits() {
        this.cancelledVisits++;
    }

    public void addToLtv(BigDecimal amount) {
        this.ltv = this.ltv.add(amount);
    }
}
