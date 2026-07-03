package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {
    @EntityGraph(attributePaths = {"client", "artist"})
    Page<Project> findByDeletedAtIsNull(Pageable pageable);

    @EntityGraph(attributePaths = {"client", "artist", "appointments"})
    Optional<Project> findByIdAndDeletedAtIsNull(UUID id);

    List<Project> findByClientIdAndDeletedAtIsNull(UUID clientId);
    List<Project> findByClientIdAndStatusInAndDeletedAtIsNull(UUID clientId, List<ProjectStatus> statuses);
    List<Project> findByArtistIdAndDeletedAtIsNull(UUID artistId);

    @EntityGraph(attributePaths = {"client", "artist"})
    Page<Project> findByStatusAndDeletedAtIsNull(ProjectStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"client", "artist"})
    @Query("""
            SELECT p FROM Project p
            WHERE p.deletedAt IS NULL
              AND (:status     IS NULL OR p.status       = :status)
              AND (:artistIds  IS NULL OR p.artist.id    IN :artistIds)
              AND (:clientId   IS NULL OR p.client.id    = :clientId)
              AND (COALESCE(:search, '') = ''
                   OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:locationId IS NULL OR p.location IS NULL OR p.location.id = :locationId)
            """)
    Page<Project> findWithFilters(
            @Param("status") ProjectStatus status,
            @Param("artistIds") List<UUID> artistIds,
            @Param("clientId") UUID clientId,
            @Param("search") String search,
            @Param("locationId") UUID locationId,
            Pageable pageable);
}
