package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.StaffStatus;
import com.inkflow.crm.domain.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffRepository extends JpaRepository<Staff, UUID> {
    @EntityGraph(attributePaths = {"locations"})
    Page<Staff> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"locations"})
    List<Staff> findByTenantIdAndDeletedAtIsNull(UUID tenantId);

    @EntityGraph(attributePaths = {"locations", "schedules"})
    Optional<Staff> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<Staff> findByEmailAndTenantIdAndDeletedAtIsNull(String email, UUID tenantId);

    @EntityGraph(attributePaths = {"locations"})
    Page<Staff> findByTenantIdAndRoleAndDeletedAtIsNull(UUID tenantId, UserRole role, Pageable pageable);
    boolean existsByEmailAndTenantIdAndDeletedAtIsNull(String email, UUID tenantId);

    @Query("SELECT s FROM Staff s JOIN s.locations l WHERE l.id = :locationId AND s.deletedAt IS NULL")
    List<Staff> findByLocationId(@Param("locationId") UUID locationId);

    @EntityGraph(attributePaths = {"locations"})
    @Query("SELECT s FROM Staff s WHERE s.tenantId = :tenantId AND s.role = 'ARTIST' AND s.deletedAt IS NULL")
    List<Staff> findArtistsByTenantId(@Param("tenantId") UUID tenantId);

    @EntityGraph(attributePaths = {"locations"})
    @Query("SELECT DISTINCT s FROM Staff s " +
           "LEFT JOIN s.locations l " +
           "WHERE s.tenantId = :tenantId AND s.deletedAt IS NULL " +
           "AND (COALESCE(:search, '') = '' OR " +
           "     LOWER(s.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(s.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:role IS NULL OR s.role = :role) " +
           "AND (:locationId IS NULL OR l.id = :locationId)")
    Page<Staff> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("role") UserRole role,
            @Param("locationId") UUID locationId,
            Pageable pageable);

    Optional<Staff> findByAuthUserIdAndDeletedAtIsNull(String authUserId);

    List<Staff> findByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, StaffStatus status);

    long countByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, StaffStatus status);

    @EntityGraph(attributePaths = {"locations", "schedules", "specialization", "portfolioImages", "dontDoList"})
    @Query("SELECT DISTINCT s FROM Staff s WHERE s.isPublic = true AND s.deletedAt IS NULL AND s.status = 'WORKING'")
    List<Staff> findAllPublicArtists();

    @EntityGraph(attributePaths = {"locations", "schedules", "specialization", "portfolioImages", "dontDoList"})
    @Query("SELECT DISTINCT s FROM Staff s WHERE s.isPublic = true AND s.deletedAt IS NULL AND s.status = 'WORKING' AND s.id = :id")
    Optional<Staff> findPublicArtistById(@Param("id") UUID id);
}
