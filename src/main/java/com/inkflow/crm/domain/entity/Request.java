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
@Table(name = "requests", indexes = {
    @Index(name = "idx_request_tenant_status", columnList = "tenant_id, status"),
    @Index(name = "idx_request_tenant_created", columnList = "tenant_id, created_at"),
    @Index(name = "idx_request_location", columnList = "location_id"),
})
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff assignedStaff;

    @Column(name = "consumer_user_id")
    private java.util.UUID consumerUserId;

    @Column(name = "tattoo_timing", length = 30)
    private String tattooTiming;

    @Column(name = "tattoo_size", length = 30)
    private String tattooSize;

    @Column(name = "body_zones", columnDefinition = "TEXT")
    private String bodyZones;

    @Column(name = "is_cover_up")
    private Boolean isCoverUp;

    @Column(name = "idea", columnDefinition = "TEXT")
    private String idea;

    @Column(name = "reference_urls", columnDefinition = "TEXT")
    private String referenceUrls;

    @Column(name = "city", length = 50)
    private String city;

    @Column(name = "contact_method", length = 20)
    private String contactMethod;

    @Column(name = "contact_value")
    private String contactValue;

    @Column(name = "sketch_url")
    private String sketchUrl;

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
