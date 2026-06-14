package com.inkflow.crm.domain.entity;

import com.inkflow.crm.module.email.enums.TemplateCategory;
import com.inkflow.crm.module.email.enums.TriggerType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "email_template",
        uniqueConstraints = @UniqueConstraint(name = "uq_email_template_tenant_builtin", columnNames = {"tenant_id", "builtin_key"}),
        indexes = @Index(name = "idx_email_template_tenant_trigger_enabled", columnList = "tenant_id, trigger_type, enabled")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 40)
    private TriggerType triggerType;

    @Column(name = "offset_minutes")
    private Integer offsetMinutes;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "deletable", nullable = false, updatable = false)
    private Boolean deletable = true;

    @Column(name = "builtin_key", length = 64, updatable = false)
    private String builtinKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private TemplateCategory category;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
