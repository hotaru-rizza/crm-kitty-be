package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {
    Page<Staff> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);
    List<Staff> findByTenantIdAndDeletedAtIsNull(UUID tenantId);
    Optional<Staff> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
    Optional<Staff> findByEmailAndTenantIdAndDeletedAtIsNull(String email, UUID tenantId);
    Page<Staff> findByTenantIdAndRoleAndDeletedAtIsNull(UUID tenantId, UserRole role, Pageable pageable);
    boolean existsByEmailAndTenantIdAndDeletedAtIsNull(String email, UUID tenantId);

    @Query("SELECT s FROM Staff s JOIN s.locations l WHERE l.id = :locationId AND s.deletedAt IS NULL")
    List<Staff> findByLocationId(@Param("locationId") UUID locationId);

    @Query("SELECT s FROM Staff s WHERE s.tenantId = :tenantId AND s.role = 'ARTIST' AND s.deletedAt IS NULL")
    List<Staff> findArtistsByTenantId(@Param("tenantId") UUID tenantId);
}
