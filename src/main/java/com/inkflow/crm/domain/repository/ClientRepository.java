package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.ClientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    Page<Client> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    Optional<Client> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);

    Optional<Client> findByPhoneAndTenantIdAndDeletedAtIsNull(String phone, UUID tenantId);

    boolean existsByPhoneAndTenantIdAndDeletedAtIsNull(String phone, UUID tenantId);

    Page<Client> findByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, ClientStatus status, Pageable pageable);

    @Query("""
            SELECT c FROM Client c
            LEFT JOIN FETCH c.tags
            WHERE c.id = :id
              AND c.tenantId = :tenantId
              AND c.deletedAt IS NULL
            """)
    Optional<Client> findByIdWithCollections(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId);

    @Query("""
            SELECT c FROM Client c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
                   OR c.phone             LIKE CONCAT('%', :search, '%')
                   OR LOWER(c.email)     LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Client> searchClients(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT c FROM Client c
            LEFT JOIN Project p ON p.client.id = c.id AND p.deletedAt IS NULL
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND (COALESCE(:search, '') = ''
                   OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
                   OR c.phone             LIKE CONCAT('%', :search, '%')
                   OR LOWER(c.email)     LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:status   IS NULL OR c.status      = :status)
              AND (:artistId IS NULL OR p.artist.id   = :artistId)
            """)
    Page<Client> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("status") ClientStatus status,
            @Param("artistId") UUID artistId,
            Pageable pageable);

    @Query("""
            SELECT c FROM Client c
            WHERE c.id IN :ids
              AND c.tenantId = :tenantId
              AND c.deletedAt IS NULL
            """)
    List<Client> findByIdInAndTenantIdAndDeletedAtIsNull(
            @Param("ids") List<UUID> ids,
            @Param("tenantId") UUID tenantId);

    @Query("""
            SELECT c FROM Client c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND c.birthDate IS NOT NULL
              AND c.birthDate = :birthDate
            """)
    List<Client> findByTenantIdAndBirthDateAndDeletedAtIsNull(
            @Param("tenantId") UUID tenantId,
            @Param("birthDate") java.time.LocalDate birthDate);

    @Query("""
            SELECT c FROM Client c
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND c.email IS NOT NULL
              AND c.email <> ''
              AND c.totalVisits > 0
              AND (c.lastVisit IS NULL OR c.lastVisit < :cutoff)
            """)
    List<Client> findInactiveClients(
            @Param("tenantId") UUID tenantId,
            @Param("cutoff") Instant cutoff);

    @Query("""
            SELECT DISTINCT c FROM Client c
            LEFT JOIN Project p ON p.client.id = c.id AND p.deletedAt IS NULL
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND c.totalVisits > 0
              AND (c.lastVisit IS NULL OR c.lastVisit < :cutoff)
              AND (COALESCE(:search, '') = ''
                   OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
                   OR c.phone             LIKE CONCAT('%', :search, '%'))
              AND (:artistId IS NULL OR p.artist.id = :artistId)
            """)
    Page<Client> findLostClients(
            @Param("tenantId") UUID tenantId,
            @Param("cutoff") Instant cutoff,
            @Param("search") String search,
            @Param("artistId") UUID artistId,
            Pageable pageable);
}
