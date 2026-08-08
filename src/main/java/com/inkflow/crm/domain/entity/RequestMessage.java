package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.RequestMessageSenderType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "request_messages",
        indexes = {
                @Index(name = "idx_request_message_request_created", columnList = "request_id, created_at"),
                @Index(name = "idx_request_message_tenant", columnList = "tenant_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class RequestMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 16)
    private RequestMessageSenderType senderType;

    @Column(name = "sender_staff_id")
    private UUID senderStaffId;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
