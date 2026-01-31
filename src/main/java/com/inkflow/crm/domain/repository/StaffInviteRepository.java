package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.StaffInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffInviteRepository extends JpaRepository<StaffInvite, UUID> {
    Optional<StaffInvite> findByToken(String token);
    Optional<StaffInvite> findByEmailAndTenantIdAndAcceptedAtIsNull(String email, UUID tenantId);
    boolean existsByEmailAndTenantIdAndAcceptedAtIsNull(String email, UUID tenantId);
}
