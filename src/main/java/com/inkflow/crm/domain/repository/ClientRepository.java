package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID>, JpaSpecificationExecutor<Client> {

    Page<Client> findByDeletedAtIsNull(Pageable pageable);

    Optional<Client> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Client> findByPhoneAndDeletedAtIsNull(String phone);

    Optional<Client> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByPhoneAndDeletedAtIsNull(String phone);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    @Query("""
            SELECT c FROM Client c
            LEFT JOIN FETCH c.tags
            WHERE c.id = :id
              AND c.deletedAt IS NULL
            """)
    Optional<Client> findByIdWithCollections(@Param("id") UUID id);

    @Query("""
            SELECT c FROM Client c
            WHERE c.deletedAt IS NULL
              AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
                   OR c.phone             LIKE CONCAT('%', :search, '%')
                   OR LOWER(c.email)     LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Client> searchClients(
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT c FROM Client c
            WHERE c.id IN :ids
              AND c.deletedAt IS NULL
            """)
    List<Client> findByIdInAndDeletedAtIsNull(@Param("ids") List<UUID> ids);

    @Query("""
            SELECT c FROM Client c
            WHERE c.deletedAt IS NULL
              AND c.birthDate IS NOT NULL
              AND c.birthDate = :birthDate
            """)
    List<Client> findByBirthDateAndDeletedAtIsNull(@Param("birthDate") java.time.LocalDate birthDate);

    @Query("""
            SELECT c FROM Client c
            WHERE c.deletedAt IS NULL
              AND c.email IS NOT NULL
              AND c.email <> ''
              AND c.totalVisits > 0
              AND (c.lastVisit IS NULL OR c.lastVisit < :cutoff)
            """)
    List<Client> findInactiveClients(@Param("cutoff") Instant cutoff);

    @Query("""
            SELECT DISTINCT c FROM Client c
            LEFT JOIN Project p ON p.client.id = c.id AND p.deletedAt IS NULL
            WHERE c.deletedAt IS NULL
              AND c.totalVisits > 0
              AND (c.lastVisit IS NULL OR c.lastVisit < :cutoff)
              AND (COALESCE(:search, '') = ''
                   OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(c.lastName)  LIKE LOWER(CONCAT('%', :search, '%'))
                   OR c.phone             LIKE CONCAT('%', :search, '%'))
              AND (:artistId IS NULL OR p.artist.id = :artistId)
            """)
    Page<Client> findLostClients(
            @Param("cutoff") Instant cutoff,
            @Param("search") String search,
            @Param("artistId") UUID artistId,
            Pageable pageable);

    @Modifying
    @Query("""
            UPDATE Client c
            SET c.dormant = true
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND c.blacklisted = false
              AND c.dormant = false
              AND c.lastVisit IS NOT NULL
              AND c.lastVisit < :cutoff
            """)
    int markDormantClients(@Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff);

    @Modifying
    @Query("""
            UPDATE Client c
            SET c.dormant = false
            WHERE c.tenantId = :tenantId
              AND c.deletedAt IS NULL
              AND c.dormant = true
              AND c.lastVisit IS NOT NULL
              AND c.lastVisit >= :cutoff
            """)
    int reactivateDormantClients(@Param("tenantId") UUID tenantId, @Param("cutoff") Instant cutoff);
}
