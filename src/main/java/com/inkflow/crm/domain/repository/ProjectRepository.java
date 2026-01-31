package com.inkflow.crm.domain.repository;

import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
