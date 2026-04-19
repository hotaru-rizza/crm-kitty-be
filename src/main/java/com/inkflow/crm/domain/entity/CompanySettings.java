package com.inkflow.crm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "company_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CompanySettings {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "sms_reminders", nullable = false)
    private Boolean smsReminders = false;

    @Column(name = "telegram_reminders", nullable = false)
    private Boolean telegramReminders = false;

    @Column(name = "email_reminders", nullable = false)
    private Boolean emailReminders = true;

    @Column(name = "email_confirmations", nullable = false)
    @Builder.Default
    private Boolean emailConfirmations = true;

    @Column(name = "email_aftercare", nullable = false)
    @Builder.Default
    private Boolean emailAftercare = false;

    @Column(name = "reminder_hours_before", nullable = false)
    private Integer reminderHoursBefore = 24;

    @Column(name = "working_hours_start", nullable = false)
    private LocalTime workingHoursStart = LocalTime.of(9, 0);

    @Column(name = "working_hours_end", nullable = false)
    private LocalTime workingHoursEnd = LocalTime.of(22, 0);

    @Column(name = "allow_online_booking", nullable = false)
    private Boolean allowOnlineBooking = true;

    @Column(name = "min_advance_hours", nullable = false)
    private Integer minAdvanceHours = 24;

    @Column(name = "max_advance_days", nullable = false)
    private Integer maxAdvanceDays = 60;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "email_templates", columnDefinition = "jsonb")
    private Map<String, Map<String, String>> emailTemplates;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
