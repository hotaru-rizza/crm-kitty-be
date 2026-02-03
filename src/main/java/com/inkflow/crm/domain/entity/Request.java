package com.inkflow.crm.domain.entity;

import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Request {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private RequestSource source;

    @Column(name = "client_name", nullable = false)
    private String clientName;

    @Column(name = "client_nickname")
    private String clientNickname;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "phone")
    private String phone;

    @Column(name = "instagram")
    private String instagram;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RequestStatus status = RequestStatus.NEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "converted_client_id")
    private Client convertedClient;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "replied_at")
    private Instant repliedAt;

    @Column(name = "converted_at")
    private Instant convertedAt;

    public void markAsReplied() {
        this.status = RequestStatus.REPLIED;
        this.repliedAt = Instant.now();
    }

    public void markAsConverted(Client client) {
        this.status = RequestStatus.CONVERTED;
        this.convertedClient = client;
        this.convertedAt = Instant.now();
    }

    public UUID getConvertedClientId() {
        return convertedClient != null ? convertedClient.getId() : null;
    }

    public void markAsSpam() {
        this.status = RequestStatus.SPAM;
    }
}
