package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestRepository extends JpaRepository<Request, UUID> {
    Page<Request> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<Request> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<Request> findByTenantIdAndStatus(UUID tenantId, RequestStatus status, Pageable pageable);
    Page<Request> findByTenantIdAndSource(UUID tenantId, RequestSource source, Pageable pageable);
    long countByTenantIdAndStatus(UUID tenantId, RequestStatus status);

    @Query("SELECT r FROM Request r WHERE r.tenantId = :tenantId " +
           "AND (CAST(:status AS string) IS NULL OR r.status = :status) " +
           "AND (CAST(:source AS string) IS NULL OR r.source = :source) " +
           "AND (CAST(:from AS timestamp) IS NULL OR r.createdAt >= :from) " +
           "AND (CAST(:to AS timestamp) IS NULL OR r.createdAt <= :to) " +
           "AND (CAST(:locationId AS string) IS NULL OR r.location.id = :locationId)")
    Page<Request> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("status") RequestStatus status,
            @Param("source") RequestSource source,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("locationId") UUID locationId,
            Pageable pageable);
}
