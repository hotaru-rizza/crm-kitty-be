package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.ProjectStatus;
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
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);
    Optional<Project> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
    List<Project> findByClientIdAndDeletedAtIsNull(UUID clientId);
    List<Project> findByClientIdAndStatusInAndDeletedAtIsNull(UUID clientId, List<ProjectStatus> statuses);
    List<Project> findByArtistIdAndDeletedAtIsNull(UUID artistId);
    Page<Project> findByTenantIdAndStatusAndDeletedAtIsNull(UUID tenantId, ProjectStatus status, Pageable pageable);

    @Query("SELECT p FROM Project p WHERE p.tenantId = :tenantId AND p.deletedAt IS NULL " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:artistId IS NULL OR p.artist.id = :artistId) " +
           "AND (:clientId IS NULL OR p.client.id = :clientId) " +
           "AND (COALESCE(:search, '') = '' OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "AND (:locationId IS NULL OR p.location.id = :locationId)")
    Page<Project> findWithFilters(
            @Param("tenantId") UUID tenantId,
            @Param("status") ProjectStatus status,
            @Param("artistId") UUID artistId,
            @Param("clientId") UUID clientId,
            @Param("search") String search,
            @Param("locationId") UUID locationId,
            Pageable pageable);
}
