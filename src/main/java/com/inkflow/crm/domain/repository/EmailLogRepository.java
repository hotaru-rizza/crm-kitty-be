package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.EmailLog;
import com.inkflow.crm.domain.enums.EmailType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface EmailLogRepository extends JpaRepository<EmailLog, UUID> {

    Page<EmailLog> findByTenantIdOrderBySentAtDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM EmailLog e WHERE e.tenantId = :tenantId " +
            "AND (:type IS NULL OR e.type = :type) " +
            "AND (:from IS NULL OR e.sentAt >= :from) " +
            "AND (:to IS NULL OR e.sentAt < :to) " +
            "ORDER BY e.sentAt DESC")
    Page<EmailLog> findFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("type") EmailType type,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    boolean existsByAppointmentIdAndType(UUID appointmentId, EmailType type);

    boolean existsByTemplateKeyAndEntityId(String templateKey, UUID entityId);

    long countByTenantIdAndSentAtAfter(UUID tenantId, Instant after);

    long countByTenantIdAndTypeAndSentAtAfter(UUID tenantId, EmailType type, Instant after);
}
