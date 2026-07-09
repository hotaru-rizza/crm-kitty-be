package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.StaffInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffInviteRepository extends JpaRepository<StaffInvite, UUID> {

    Optional<StaffInvite> findByToken(String token);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM StaffInvite i WHERE i.token = :token")
    Optional<StaffInvite> findByTokenForUpdate(@Param("token") String token);

    Optional<StaffInvite> findByEmailAndAcceptedAtIsNull(String email);

    Optional<StaffInvite> findFirstByTenantIdAndEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(
            UUID tenantId, String email);

    List<StaffInvite> findByTenantIdAndAcceptedAtIsNullOrderByCreatedAtDesc(UUID tenantId);

    List<StaffInvite> findByAcceptedAtIsNullAndExpiresAtBefore(Instant cutoff);
}
