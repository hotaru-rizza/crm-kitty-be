package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.EmailStatus;
import com.inkflow.crm.domain.enums.EmailType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(name = "recipient_name")
    private String recipientName;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private EmailType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmailStatus status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    /**
     * New: TemplateKey name. Populated for all emails sent via NotificationSender.
     * Legacy appointment emails keep only the old EmailType until migration is complete.
     */
    @Column(name = "template_key", length = 64)
    private String templateKey;

    /**
     * General entity reference for idempotency checks (appointment ID, client ID, etc.).
     * Replaces appointment_id for non-appointment emails.
     */
    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @PrePersist
    protected void onCreate() {
        if (sentAt == null) {
            sentAt = Instant.now();
        }
    }
}
