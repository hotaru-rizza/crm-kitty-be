package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.SalaryType;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.UserRole;
import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "staff", indexes = {
    @Index(name = "idx_staff_tenant_deleted", columnList = "tenant_id, deleted_at"),
    @Index(name = "idx_staff_auth_user", columnList = "auth_user_id"),
    @Index(name = "idx_staff_email_tenant", columnList = "email, tenant_id"),
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Staff extends BaseEntity {

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(name = "avatar")
    private String avatar;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    @Column(name = "calendar_color", nullable = false)
    private String calendarColor;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "staff_specializations", joinColumns = @JoinColumn(name = "staff_id"))
    @Column(name = "specialization")
    @org.hibernate.annotations.BatchSize(size = 50)
    @Builder.Default
    private List<String> specialization = new ArrayList<>();

    @Column(name = "bio")
    private String bio;

    @Column(name = "instagram")
    private String instagram;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StaffStatus status = StaffStatus.WORKING;

    @ManyToMany
    @JoinTable(
            name = "staff_locations",
            joinColumns = @JoinColumn(name = "staff_id"),
            inverseJoinColumns = @JoinColumn(name = "location_id")
    )
    @Builder.Default
    private Set<Location> locations = new HashSet<>();

    @OneToMany(mappedBy = "staff", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StaffSchedule> schedules = new ArrayList<>();

    @OneToMany(mappedBy = "staff", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ArtistServicePricing> servicePricings = new ArrayList<>();

    @OneToMany(mappedBy = "staff", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LeaveRequest> leaveRequests = new ArrayList<>();

    @Column(name = "auth_user_id")
    private String authUserId;

    @Column(name = "google_access_token", columnDefinition = "TEXT")
    private String googleAccessToken;

    @Column(name = "google_refresh_token", columnDefinition = "TEXT")
    private String googleRefreshToken;

    @Column(name = "google_calendar_id")
    private String googleCalendarId;

    @Column(name = "google_token_expires_at")
    private java.time.Instant googleTokenExpiresAt;

    @Column(name = "google_calendar_email")
    private String googleCalendarEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_type", nullable = false)
    @Builder.Default
    private SalaryType salaryType = SalaryType.NONE;

    @Column(name = "salary_rate", precision = 10, scale = 2)
    private BigDecimal salaryRate;

    @Column(name = "bank_details", columnDefinition = "TEXT")
    private String bankDetails;

    public boolean isGoogleCalendarConnected() {
        return googleRefreshToken != null && !googleRefreshToken.isBlank();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isAvailable() {
        return status.isAvailable() && !isDeleted();
    }
}
