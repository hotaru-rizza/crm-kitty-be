package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, UUID>,
        JpaSpecificationExecutor<AuditLogEntry> {

    @Modifying
    @Query("DELETE FROM AuditLogEntry e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);

    boolean existsByTenantIdAndActorIdAndActionAndDetails(
            UUID tenantId,
            UUID actorId,
            String action,
            String details
    );
}
