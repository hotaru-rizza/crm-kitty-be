package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.EmailMessage;
import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.module.email.enums.TriggerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailMessageRepository extends JpaRepository<EmailMessage, UUID> {

    @Query(value = """
            SELECT * FROM email_message
            WHERE status = 'PENDING'
              AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EmailMessage> findPendingForProcessing(@Param("now") Instant now, @Param("limit") int limit);

    boolean existsByTriggerTypeAndEntityIdAndStatus(
            TriggerType triggerType, UUID entityId, EmailMessageStatus status);

    boolean existsByDedupeKey(String dedupeKey);

    @Query("SELECT e FROM EmailMessage e WHERE e.tenantId = :tenantId " +
            "AND (CAST(:triggerType AS string) IS NULL OR e.triggerType = :triggerType) " +
            "AND (CAST(:status AS string) IS NULL OR e.status = :status) " +
            "AND (CAST(:from AS timestamp) IS NULL OR COALESCE(e.sentAt, e.createdAt) >= :from) " +
            "AND (CAST(:to AS timestamp) IS NULL OR COALESCE(e.sentAt, e.createdAt) < :to) " +
            "ORDER BY COALESCE(e.sentAt, e.createdAt) DESC")
    Page<EmailMessage> findFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("triggerType") TriggerType triggerType,
            @Param("status") EmailMessageStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    @Query("SELECT e FROM EmailMessage e WHERE e.tenantId = :tenantId " +
            "AND (CAST(:triggerType AS string) IS NULL OR e.triggerType = :triggerType) " +
            "AND (CAST(:status AS string) IS NULL OR e.status = :status) " +
            "AND (CAST(:from AS timestamp) IS NULL OR COALESCE(e.sentAt, e.createdAt) >= :from) " +
            "AND (CAST(:to AS timestamp) IS NULL OR COALESCE(e.sentAt, e.createdAt) < :to) " +
            "AND (LOWER(e.recipientEmail) LIKE :searchPattern " +
            "     OR LOWER(COALESCE(e.recipientName, '')) LIKE :searchPattern " +
            "     OR LOWER(e.subject) LIKE :searchPattern) " +
            "ORDER BY COALESCE(e.sentAt, e.createdAt) DESC")
    Page<EmailMessage> findFilteredWithSearch(
            @Param("tenantId") UUID tenantId,
            @Param("triggerType") TriggerType triggerType,
            @Param("status") EmailMessageStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    long countByTenantIdAndStatusAndCreatedAtAfter(UUID tenantId, EmailMessageStatus status, Instant after);
}
