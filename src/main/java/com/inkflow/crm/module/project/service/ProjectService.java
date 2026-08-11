package com.inkflow.crm.module.project.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.GalleryPhoto;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.GalleryStage;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.GalleryPhotoRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.domain.repository.ProjectSpecifications;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.project.dto.*;
import com.inkflow.crm.module.project.mapper.ProjectMapper;
import com.inkflow.crm.module.project.support.ProjectAccessGuard;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.LocationScope;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final StaffRepository staffRepository;
    private final GalleryPhotoRepository galleryPhotoRepository;
    private final LocationRepository locationRepository;
    private final RolePermissionService rolePermissionService;
    private final ProjectMapper projectMapper;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;
    private final ProjectAccessGuard projectAccessGuard;

    @Transactional(readOnly = true)
    public PageResult<ProjectDto> getAllProjects(PageRequest pageRequest, ProjectFilterRequest filter, UUID locationId) {
        UUID effectiveLocationId = LocationScope.resolveFilter(locationId).orElse(null);
        Page<Project> page = getProjectsPage(pageRequest, filter, effectiveLocationId);
        List<ProjectDto> data = page.getContent().stream().map(projectMapper::toListDto).toList();
        return new PageResult<>(data, PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public ProjectDto getProjectById(UUID id) {
        Project project = requireProject(SecurityUtils.getCurrentTenantId(), id);
        projectAccessGuard.requireView(project);
        return mapToDto(project);
    }

    @Transactional
    public ProjectDto createProject(CreateProjectRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        var client = clientRepository.findByIdAndDeletedAtIsNull(request.getClientId())
                .orElseThrow(() -> ResourceNotFoundException.client(request.getClientId().toString()));
        var artist = staffRepository.findByIdAndDeletedAtIsNull(request.getArtistId())
                .orElseThrow(() -> ResourceNotFoundException.staff(request.getArtistId().toString()));

        var location = request.getLocationId() != null
                ? locationRepository.findByIdAndDeletedAtIsNull(request.getLocationId()).orElse(null)
                : null;

        Project project = Project.builder()
                .tenantId(tenantId)
                .title(request.getTitle())
                .description(request.getDescription())
                .client(client)
                .artist(artist)
                .location(location)
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(request.getEstimatedCost() != null
                        ? request.getEstimatedCost()
                        : BigDecimal.ZERO)
                .totalPaid(BigDecimal.ZERO)
                .totalSessions(request.getTotalSessions() != null
                        ? request.getTotalSessions()
                        : 0)
                .completedSessions(0)
                .sketchImage(request.getSketchImage())
                .build();

        project = projectRepository.save(project);
        log.info("Project created: tenantId={} projectId={}", tenantId, project.getId());
        auditRecorder.record(
                AuditAction.CREATE,
                AuditEntityType.PROJECT,
                project.getId().toString(),
                auditLabelFormatter.project(project.getTitle()),
                client.getId()
        );
        return mapToDto(project);
    }

    @Transactional
    public ProjectDto updateProject(UUID id, UpdateProjectRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Project project = requireProject(tenantId, id);
        projectAccessGuard.requireEdit(project);

        applyUpdate(tenantId, project, request);
        project = projectRepository.save(project);

        log.info("Project updated: tenantId={} projectId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.PROJECT,
                project.getId().toString(),
                auditLabelFormatter.project(project.getTitle()),
                project.getClient().getId()
        );
        return mapToDto(project);
    }

    @Transactional
    public void deleteProject(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Project project = requireProject(tenantId, id);
        projectAccessGuard.requireEdit(project);

        if (project.getStatus() != ProjectStatus.ARCHIVED) {
            throw BusinessRuleException.projectDeleteRequiresArchive();
        }

        project.softDelete();
        projectRepository.save(project);
        log.info("Project deleted: tenantId={} projectId={}", tenantId, id);
        auditRecorder.record(
                AuditAction.DELETE,
                AuditEntityType.PROJECT,
                project.getId().toString(),
                auditLabelFormatter.project(project.getTitle()),
                project.getClient().getId()
        );
    }

    @Transactional
    public ProjectDto.PhotoDto addPhoto(UUID projectId, String url, String stage) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Project project = requireProject(tenantId, projectId);
        projectAccessGuard.requireEdit(project);

        GalleryPhoto photo = GalleryPhoto.builder()
                .tenantId(tenantId)
                .project(project)
                .url(url)
                .stage(GalleryStage.fromValue(stage))
                .uploadedBy(SecurityUtils.getCurrentUserId())
                .build();

        photo = galleryPhotoRepository.save(photo);
        log.info("Project photo added: tenantId={} projectId={} photoId={}", tenantId, projectId, photo.getId());
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.PROJECT,
                project.getId().toString(),
                auditLabelFormatter.project(project.getTitle()),
                project.getClient().getId(),
                "Додано фото"
        );
        return projectMapper.toPhotoDto(photo);
    }

    @Transactional
    public void deletePhoto(UUID projectId, UUID photoId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Project project = requireProject(tenantId, projectId);
        projectAccessGuard.requireEdit(project);

        GalleryPhoto photo = galleryPhotoRepository.findByIdAndProjectId(photoId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PROJECT_NOT_FOUND, "Photo not found: " + photoId));

        galleryPhotoRepository.delete(photo);
        log.info("Project photo deleted: tenantId={} projectId={} photoId={}", tenantId, projectId, photoId);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.PROJECT,
                project.getId().toString(),
                auditLabelFormatter.project(project.getTitle()),
                project.getClient().getId(),
                "Видалено фото"
        );
    }

    private Page<Project> getProjectsPage(PageRequest pageRequest, ProjectFilterRequest filter, UUID locationId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        ProjectFilterRequest effectiveFilter = filter != null ? filter : new ProjectFilterRequest();

        if (!rolePermissionService.hasPermission(tenantId, SecurityUtils.getCurrentUserRole(), Permission.PROJECTS_VIEW_ALL.getValue())) {
            effectiveFilter.setOnlyMine(true);
        }

        ProjectStatus projectStatus = effectiveFilter.getStatus() != null
                ? ProjectStatus.fromValue(effectiveFilter.getStatus())
                : null;

        List<UUID> effectiveArtistIds = Boolean.TRUE.equals(effectiveFilter.getOnlyMine())
                ? List.of(SecurityUtils.getCurrentUserId())
                : effectiveFilter.getArtistId();

        Specification<Project> spec = Specification.where(ProjectSpecifications.notDeleted())
                .and(ProjectSpecifications.excludeArchivedWhenNoStatusFilter(effectiveFilter.getStatus()))
                .and(ProjectSpecifications.statusIs(projectStatus))
                .and(ProjectSpecifications.artistIn(effectiveArtistIds))
                .and(ProjectSpecifications.clientIs(effectiveFilter.getClientId()))
                .and(ProjectSpecifications.searchLike(effectiveFilter.getSearch()))
                .and(ProjectSpecifications.locationIs(locationId))
                .and(ProjectSpecifications.budgetBetween(effectiveFilter.getEstimatedCostMin(), effectiveFilter.getEstimatedCostMax()))
                .and(ProjectSpecifications.paidPercentBetween(effectiveFilter.getPaidPercentMin(), effectiveFilter.getPaidPercentMax()))
                .and(ProjectSpecifications.totalSessionsBetween(effectiveFilter.getTotalSessionsMin(), effectiveFilter.getTotalSessionsMax()))
                .and(ProjectSpecifications.completedSessionsBetween(effectiveFilter.getCompletedSessionsMin(), effectiveFilter.getCompletedSessionsMax()))
                .and(ProjectSpecifications.createdBetween(effectiveFilter.getCreatedAtFrom(), effectiveFilter.getCreatedAtTo()))
                .and(ProjectSpecifications.updatedBetween(effectiveFilter.getUpdatedAtFrom(), effectiveFilter.getUpdatedAtTo()))
                .and(ProjectSpecifications.hasSketch(effectiveFilter.getHasSketch()))
                .and(ProjectSpecifications.hasPhotos(effectiveFilter.getHasPhotos()));

        return projectRepository.findAll(spec, pageRequest.toPageable());
    }

    private Project requireProject(UUID tenantId, UUID id) {
        return projectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ResourceNotFoundException.project(id.toString()));
    }

    private void applyUpdate(UUID tenantId, Project project, UpdateProjectRequest request) {
        if (request.getTitle() != null) {
            project.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            project.setDescription(request.getDescription());
        }
        if (request.getEstimatedCost() != null) {
            project.setEstimatedCost(request.getEstimatedCost());
        }
        if (request.getTotalSessions() != null) {
            project.setTotalSessions(request.getTotalSessions());
        }
        if (request.getArtistId() != null) {
            UUID currentArtistId = project.getArtist() != null ? project.getArtist().getId() : null;
            if (!request.getArtistId().equals(currentArtistId)) {
                projectAccessGuard.requireLeadReassignment(request.getArtistId());
                var artist = staffRepository.findByIdAndDeletedAtIsNull(request.getArtistId())
                        .orElseThrow(() -> ResourceNotFoundException.staff(request.getArtistId().toString()));
                project.setArtist(artist);
            }
        }
        if (request.getStatus() != null) {
            project.setStatus(ProjectStatus.fromValue(request.getStatus()));
        }
        if (request.getSketchImage() != null) {
            project.setSketchImage(request.getSketchImage().isBlank() ? null : request.getSketchImage());
        }
    }

    private ProjectDto mapToDto(Project project) {
        List<ProjectDto.PhotoDto> photos = galleryPhotoRepository.findByProjectId(project.getId())
                .stream()
                .map(projectMapper::toPhotoDto)
                .toList();

        return projectMapper.toDto(project, photos, projectMapper.toSessionDtos(project));
    }
}
