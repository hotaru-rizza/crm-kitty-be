package com.inkflow.crm.module.project.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
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
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.project.dto.*;
import com.inkflow.crm.module.project.mapper.ProjectMapper;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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

    @Transactional(readOnly = true)
    public PageResult<ProjectDto> getAllProjects(
            PageRequest pageRequest,
            String status,
            List<UUID> artistIds,
            UUID clientId,
            String search,
            Boolean onlyMine,
            UUID locationId) {
        Page<Project> page = getProjectsPage(pageRequest, status, artistIds, clientId, search, onlyMine, locationId);
        List<ProjectDto> data = page.getContent().stream().map(projectMapper::toListDto).toList();
        return new PageResult<>(data, PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public ProjectDto getProjectById(UUID id) {
        return mapToDto(requireProject(SecurityUtils.getCurrentTenantId(), id));
    }

    @Transactional
    public ProjectDto createProject(CreateProjectRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        var client = clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getClientId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(request.getClientId().toString()));
        var artist = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getArtistId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(request.getArtistId().toString()));

        var location = request.getLocationId() != null
                ? locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getLocationId(), tenantId).orElse(null)
                : null;

        Project project = Project.builder()
                .tenantId(tenantId)
                .title(request.getTitle())
                .description(request.getDescription())
                .client(client)
                .artist(artist)
                .location(location)
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(request.getEstimatedCost())
                .totalPaid(BigDecimal.ZERO)
                .totalSessions(request.getTotalSessions())
                .completedSessions(0)
                .sketchImage(request.getSketchImage())
                .build();

        project = projectRepository.save(project);
        log.info("Project created: tenantId={} projectId={}", tenantId, project.getId());
        return mapToDto(project);
    }

    @Transactional
    public ProjectDto updateProject(UUID id, UpdateProjectRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Project project = requireProject(tenantId, id);

        applyUpdate(tenantId, project, request);
        project = projectRepository.save(project);

        log.info("Project updated: tenantId={} projectId={}", tenantId, id);
        return mapToDto(project);
    }

    @Transactional
    public void deleteProject(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Project project = requireProject(tenantId, id);

        project.softDelete();
        projectRepository.save(project);
        log.info("Project deleted: tenantId={} projectId={}", tenantId, id);
    }

    @Transactional
    public ProjectDto.PhotoDto addPhoto(UUID projectId, String url, String stage) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Project project = requireProject(tenantId, projectId);

        GalleryPhoto photo = GalleryPhoto.builder()
                .tenantId(tenantId)
                .project(project)
                .url(url)
                .stage(GalleryStage.fromValue(stage))
                .uploadedBy(SecurityUtils.getCurrentUserId())
                .build();

        photo = galleryPhotoRepository.save(photo);
        log.info("Project photo added: tenantId={} projectId={} photoId={}", tenantId, projectId, photo.getId());
        return projectMapper.toPhotoDto(photo);
    }

    @Transactional
    public void deletePhoto(UUID projectId, UUID photoId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        requireProject(tenantId, projectId);

        GalleryPhoto photo = galleryPhotoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PROJECT_NOT_FOUND, "Photo not found: " + photoId));

        galleryPhotoRepository.delete(photo);
        log.info("Project photo deleted: tenantId={} projectId={} photoId={}", tenantId, projectId, photoId);
    }

    private Page<Project> getProjectsPage(
            PageRequest pageRequest,
            String status,
            List<UUID> artistIds,
            UUID clientId,
            String search,
            Boolean onlyMine,
            UUID locationId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        ProjectStatus projectStatus = status != null ? ProjectStatus.fromValue(status) : null;

        if (!rolePermissionService.hasPermission(tenantId, SecurityUtils.getCurrentUserRole(), Permission.PROJECTS_VIEW_ALL.getValue())) {
            onlyMine = true;
        }

        List<UUID> effectiveArtistIds = Boolean.TRUE.equals(onlyMine)
                ? List.of(SecurityUtils.getCurrentUserId())
                : artistIds;

        return projectRepository.findWithFilters(tenantId, projectStatus, effectiveArtistIds, clientId, search, locationId, pageRequest.toPageable());
    }

    private Project requireProject(UUID tenantId, UUID id) {
        return projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
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
            var artist = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getArtistId(), tenantId)
                    .orElseThrow(() -> ResourceNotFoundException.staff(request.getArtistId().toString()));
            project.setArtist(artist);
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
