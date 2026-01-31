package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RequestRepository extends JpaRepository<Request, UUID> {
    Page<Request> findByTenantId(UUID tenantId, Pageable pageable);
    Optional<Request> findByIdAndTenantId(UUID id, UUID tenantId);
    Page<Request> findByTenantIdAndStatus(UUID tenantId, RequestStatus status, Pageable pageable);
    Page<Request> findByTenantIdAndSource(UUID tenantId, RequestSource source, Pageable pageable);
    long countByTenantIdAndStatus(UUID tenantId, RequestStatus status);
}
