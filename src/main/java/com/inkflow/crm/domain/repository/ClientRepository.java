package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.ClientStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {
    Page<Client> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);
    Optional<Client> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
    Optional<Client> findByPhoneAndTenantIdAndDeletedAtIsNull(String phone, UUID tenantId);
    boolean existsByPhoneAndTenantIdAndDeletedAtIsNull(String phone, UUID tenantId);
    Page<Client> findByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, ClientStatus status, Pageable pageable);

    @Query("SELECT c FROM Client c WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL AND " +
           "(LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "c.phone LIKE CONCAT('%', :search, '%') OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Client> searchClients(@Param("tenantId") UUID tenantId, @Param("search") String search, Pageable pageable);

    @Query("SELECT DISTINCT c FROM Client c " +
           "LEFT JOIN Project p ON p.client.id = c.id AND p.deletedAt IS NULL " +
           "WHERE c.tenantId = :tenantId AND c.deletedAt IS NULL " +
           "AND (COALESCE(:search, '') = '' OR " +
           "     LOWER(c.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(c.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     c.phone LIKE CONCAT('%', :search, '%') OR " +
           "     LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:status IS NULL OR c.status = :status) " +
           "AND (:artistId IS NULL OR p.artist.id = :artistId)")
    Page<Client> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("search") String search,
            @Param("status") ClientStatus status,
            @Param("artistId") UUID artistId,
            Pageable pageable);
}
