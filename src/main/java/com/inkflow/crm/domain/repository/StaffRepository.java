package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AccountStatus;
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
    Page<Staff> findByDeletedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"locations"})
    List<Staff> findByDeletedAtIsNull();

    @EntityGraph(attributePaths = {"locations", "schedules"})
    Optional<Staff> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Staff> findByEmailAndDeletedAtIsNull(String email);

    @EntityGraph(attributePaths = {"locations"})
    Optional<Staff> findByAuthUserIdAndDeletedAtIsNull(String authUserId);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    @Query("""
            SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END
            FROM Staff s
            WHERE s.tenantId = :tenantId
              AND LOWER(s.email) = LOWER(:email)
              AND s.deletedAt IS NULL
            """)
    boolean existsByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(
            @Param("tenantId") UUID tenantId,
            @Param("email") String email);

    List<Staff> findByStatusAndDeletedAtIsNull(StaffStatus status);

    long countByStatusAndDeletedAtIsNull(StaffStatus status);

    long countByDeletedAtIsNull();

    @EntityGraph(attributePaths = {"locations"})
    Page<Staff> findByRoleAndDeletedAtIsNull(UserRole role, Pageable pageable);

    @Query("""
            SELECT s FROM Staff s
            WHERE s.id IN :ids
              AND s.deletedAt IS NULL
            """)
    List<Staff> findByIdInAndDeletedAtIsNull(@Param("ids") List<UUID> ids);

    @Query("""
            SELECT s FROM Staff s
            JOIN s.locations l
            WHERE l.id = :locationId
              AND s.deletedAt IS NULL
            """)
    List<Staff> findByLocationId(@Param("locationId") UUID locationId);

    @Query("""
            SELECT s FROM Staff s
            JOIN s.locations l
            WHERE l.id = :locationId
              AND s.isServiceProvider = true
              AND s.deletedAt IS NULL
            """)
    List<Staff> findServiceProvidersByLocationId(@Param("locationId") UUID locationId);

    @EntityGraph(attributePaths = {"locations"})
    @Query("""
            SELECT s FROM Staff s
            WHERE s.role = 'ARTIST'
              AND s.deletedAt IS NULL
            """)
    List<Staff> findArtists();

    @EntityGraph(attributePaths = {"locations"})
    @Query("""
            SELECT DISTINCT s FROM Staff s
            LEFT JOIN s.locations l
            WHERE s.deletedAt IS NULL
              AND (COALESCE(:search, '') = ''
                   OR LOWER(s.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(s.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(s.email)     LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:role          IS NULL OR s.role           = :role)
              AND (:locationId    IS NULL OR l.id             = :locationId)
              AND (:accountStatus IS NULL OR s.accountStatus  = :accountStatus)
            """)
    Page<Staff> findWithFilters(
            @Param("search") String search,
            @Param("role") UserRole role,
            @Param("locationId") UUID locationId,
            @Param("accountStatus") AccountStatus accountStatus,
            Pageable pageable);

    @EntityGraph(attributePaths = {"locations", "schedules", "specialization", "portfolioImages", "dontDoList"})
    @Query("""
            SELECT DISTINCT s FROM Staff s
            WHERE s.isPublic = true
              AND s.isServiceProvider = true
              AND s.deletedAt IS NULL
              AND s.status = com.inkflow.crm.domain.enums.StaffStatus.WORKING
            """)
    List<Staff> findAllPublicArtists();

    @EntityGraph(attributePaths = {"locations", "schedules", "specialization", "portfolioImages", "dontDoList"})
    @Query("""
            SELECT DISTINCT s FROM Staff s
            WHERE s.isPublic = true
              AND s.isServiceProvider = true
              AND s.deletedAt IS NULL
              AND s.status = com.inkflow.crm.domain.enums.StaffStatus.WORKING
              AND s.id = :id
            """)
    Optional<Staff> findPublicArtistById(@Param("id") UUID id);
}
