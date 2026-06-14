package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.module.email.enums.TriggerType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "email_message",
        indexes = {
                @Index(name = "idx_email_message_status_next_attempt", columnList = "status, next_attempt_at"),
                @Index(name = "idx_email_message_tenant_created", columnList = "tenant_id, created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "template_id")
    private UUID templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 40)
    private TriggerType triggerType;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private EmailMessageStatus status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "dedupe_key", length = 128, unique = true)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (attempts == null) {
            attempts = 0;
        }
    }
}
