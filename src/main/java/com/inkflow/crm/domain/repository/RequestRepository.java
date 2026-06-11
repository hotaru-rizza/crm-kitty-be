package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestRepository extends JpaRepository<Request, UUID> {
    Page<Request> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<Request> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<Request> findByTenantIdAndStatus(UUID tenantId, RequestStatus status, Pageable pageable);
    Page<Request> findByTenantIdAndSource(UUID tenantId, RequestSource source, Pageable pageable);
    long countByTenantIdAndStatus(UUID tenantId, RequestStatus status);

    @EntityGraph(attributePaths = {"assignedStaff"})
    List<Request> findByConsumerUserIdOrderByCreatedAtDesc(UUID consumerUserId);

    @Query("SELECT r FROM Request r WHERE r.tenantId = :tenantId " +
           "AND (CAST(:status AS string) IS NULL OR r.status = :status) " +
           "AND (:sources IS NULL OR r.source IN :sources) " +
           "AND (CAST(:from AS timestamp) IS NULL OR r.createdAt >= :from) " +
           "AND (CAST(:to AS timestamp) IS NULL OR r.createdAt <= :to) " +
           "AND (CAST(:locationId AS string) IS NULL OR r.location.id = :locationId)")
    Page<Request> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("status") RequestStatus status,
            @Param("sources") List<RequestSource> sources,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("locationId") UUID locationId,
            Pageable pageable);
}
