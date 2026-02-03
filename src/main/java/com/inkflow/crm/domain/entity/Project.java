package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Project extends BaseEntity {

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private Staff artist;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectStatus status = ProjectStatus.IN_PROGRESS;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "estimated_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "total_paid", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPaid = BigDecimal.ZERO;

    @Column(name = "total_sessions", nullable = false)
    private Integer totalSessions;

    @Column(name = "completed_sessions", nullable = false)
    private Integer completedSessions = 0;

    @OneToMany(mappedBy = "project")
    @Builder.Default
    private List<Appointment> appointments = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<GalleryPhoto> photos = new ArrayList<>();

    public void incrementCompletedSessions() {
        this.completedSessions++;
        if (this.completedSessions >= this.totalSessions) {
            this.status = ProjectStatus.COMPLETED;
        }
    }

    public void addPayment(BigDecimal amount) {
        this.totalPaid = this.totalPaid.add(amount);
    }

    public BigDecimal getRemainingAmount() {
        return estimatedCost.subtract(totalPaid);
    }

    public int getRemainingSessions() {
        return totalSessions - completedSessions;
    }

    public boolean isActive() {
        return status.isActive();
    }
}
