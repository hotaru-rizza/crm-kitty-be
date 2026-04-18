package com.inkflow.crm.module.project.service;

import com.inkflow.crm.common.dto.PageRequest;
import java.util.Comparator;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.GalleryPhoto;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.GalleryStage;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.GalleryPhotoRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.client.dto.ClientSummaryDto;
import com.inkflow.crm.module.project.dto.*;
import com.inkflow.crm.module.settings.service.SettingsService;
import com.inkflow.crm.module.staff.dto.StaffSummaryDto;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final StaffRepository staffRepository;
    private final GalleryPhotoRepository galleryPhotoRepository;
    private final LocationRepository locationRepository;
    private final SettingsService settingsService;

    @Transactional(readOnly = true)
    public List<ProjectDto> getAllProjects(PageRequest pageRequest, String status, List<UUID> artistIds, UUID clientId, String search, Boolean onlyMine, UUID locationId) {
        Page<Project> page = getProjectsPage(pageRequest, status, artistIds, clientId, search, onlyMine, locationId);
        return page.getContent().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PaginationDto getPagination(PageRequest pageRequest, String status, List<UUID> artistIds, UUID clientId, String search, Boolean onlyMine, UUID locationId) {
        Page<Project> page = getProjectsPage(pageRequest, status, artistIds, clientId, search, onlyMine, locationId);
        return PaginationDto.from(page);
    }

    private Page<Project> getProjectsPage(PageRequest pageRequest, String status, List<UUID> artistIds, UUID clientId, String search, Boolean onlyMine, UUID locationId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        ProjectStatus projectStatus = status != null ? ProjectStatus.fromValue(status) : null;
        
        if (!settingsService.hasPermission(tenantId, SecurityUtils.getCurrentUserRole(), "projects.view_all")) {
            onlyMine = true;
        }
        List<UUID> effectiveArtistIds = artistIds;
        if (Boolean.TRUE.equals(onlyMine)) {
            effectiveArtistIds = List.of(SecurityUtils.getCurrentUserId());
        }

        return projectRepository.findWithFilters(tenantId, projectStatus, effectiveArtistIds, clientId, search, locationId, pageRequest.toPageable());
    }

    @Transactional(readOnly = true)
    public ProjectDto getProjectById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Project project = projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.project(id.toString()));
        return mapToDto(project);
    }

    @Transactional
    public ProjectDto createProject(CreateProjectRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Client client = clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getClientId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(request.getClientId().toString()));
        Staff artist = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getArtistId(), tenantId)
                .orElseThrow(() -> ResourceNotFoundException.staff(request.getArtistId().toString()));

        Location location = null;
        if (request.getLocationId() != null) {
            location = locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getLocationId(), tenantId).orElse(null);
        }

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
        return mapToDto(project);
    }

    @Transactional
    public ProjectDto updateProject(UUID id, UpdateProjectRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Project project = projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.project(id.toString()));

        if (request.getTitle() != null) project.setTitle(request.getTitle());
        if (request.getDescription() != null) project.setDescription(request.getDescription());
        if (request.getEstimatedCost() != null) project.setEstimatedCost(request.getEstimatedCost());
        if (request.getTotalSessions() != null) project.setTotalSessions(request.getTotalSessions());

        if (request.getArtistId() != null) {
            Staff artist = staffRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getArtistId(), tenantId)
                    .orElseThrow(() -> ResourceNotFoundException.staff(request.getArtistId().toString()));
            project.setArtist(artist);
        }

        if (request.getStatus() != null) {
            project.setStatus(ProjectStatus.fromValue(request.getStatus()));
        }

        if (request.getSketchImage() != null) {
            project.setSketchImage(request.getSketchImage().isBlank() ? null : request.getSketchImage());
        }

        project = projectRepository.save(project);
        return mapToDto(project);
    }

    @Transactional
    public void deleteProject(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Project project = projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.project(id.toString()));
        project.softDelete();
        projectRepository.save(project);
    }

    @Transactional
    public ProjectDto.PhotoDto addPhoto(UUID projectId, String url, String stage) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId();

        Project project = projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(projectId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.project(projectId.toString()));

        GalleryPhoto photo = GalleryPhoto.builder()
                .tenantId(tenantId)
                .project(project)
                .url(url)
                .stage(GalleryStage.fromValue(stage))
                .uploadedBy(userId)
                .build();

        photo = galleryPhotoRepository.save(photo);

        return ProjectDto.PhotoDto.builder()
                .id(photo.getId())
                .url(photo.getUrl())
                .stage(photo.getStage().getValue())
                .uploadedAt(photo.getUploadedAt())
                .build();
    }

    @Transactional
    public void deletePhoto(UUID projectId, UUID photoId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        projectRepository.findByIdAndTenantIdAndDeletedAtIsNull(projectId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.project(projectId.toString()));

        GalleryPhoto photo = galleryPhotoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        com.inkflow.crm.common.exception.ErrorCode.PROJECT_NOT_FOUND, "Photo not found: " + photoId));

        galleryPhotoRepository.delete(photo);
    }

    private ProjectDto mapToDto(Project project) {
        List<ProjectDto.PhotoDto> photos = galleryPhotoRepository.findByProjectId(project.getId())
                .stream()
                .map(p -> ProjectDto.PhotoDto.builder()
                        .id(p.getId())
                        .url(p.getUrl())
                        .stage(p.getStage().getValue())
                        .uploadedAt(p.getUploadedAt())
                        .build())
                .collect(Collectors.toList());

        List<ProjectDto.SessionDto> sessions = project.getAppointments().stream()
                .filter(a -> a.getDeletedAt() == null)
                .sorted(Comparator.comparing(com.inkflow.crm.domain.entity.Appointment::getStartTime))
                .map(a -> ProjectDto.SessionDto.builder()
                        .id(a.getId())
                        .startTime(a.getStartTime())
                        .endTime(a.getEndTime())
                        .status(a.getStatus().getValue())
                        .serviceId(a.getService() != null ? a.getService().getId() : null)
                        .serviceName(a.getService() != null ? a.getService().getTitle() : null)
                        .serviceColor(a.getService() != null ? a.getService().getColor() : null)
                        .price(a.getPrice())
                        .finalPrice(a.getFinalPrice())
                        .waiverSigned(a.getWaiverSigned())
                        .notes(a.getNotes())
                        .photosCount(a.getPhotos() != null ? a.getPhotos().size() : 0)
                        .build())
                .collect(Collectors.toList());

        return ProjectDto.builder()
                .id(project.getId())
                .title(project.getTitle())
                .description(project.getDescription())
                .client(ClientSummaryDto.builder()
                        .id(project.getClient().getId())
                        .firstName(project.getClient().getFirstName())
                        .lastName(project.getClient().getLastName())
                        .phone(project.getClient().getPhone())
                        .avatar(project.getClient().getAvatar())
                        .hasMedicalConditions(project.getClient().hasMedicalConditions())
                        .build())
                .artist(StaffSummaryDto.builder()
                        .id(project.getArtist().getId())
                        .firstName(project.getArtist().getFirstName())
                        .lastName(project.getArtist().getLastName())
                        .avatar(project.getArtist().getAvatar())
                        .calendarColor(project.getArtist().getCalendarColor())
                        .role(project.getArtist().getRole().getValue())
                        .build())
                .status(project.getStatus().getValue())
                .estimatedCost(project.getEstimatedCost())
                .totalPaid(project.getTotalPaid())
                .totalSessions(project.getTotalSessions())
                .completedSessions(project.getCompletedSessions())
                .sketchImage(project.getSketchImage())
                .createdAt(project.getCreatedAt())
                .photos(photos)
                .sessions(sessions)
                .build();
    }
}
