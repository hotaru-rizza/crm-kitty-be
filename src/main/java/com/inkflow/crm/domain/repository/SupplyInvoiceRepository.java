package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.SupplyInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupplyInvoiceRepository extends JpaRepository<SupplyInvoice, UUID> {
    Page<SupplyInvoice> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);
    Optional<SupplyInvoice> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
