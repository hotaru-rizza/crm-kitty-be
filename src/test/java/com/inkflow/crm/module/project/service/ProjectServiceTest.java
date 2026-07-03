package com.inkflow.crm.module.project.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.GalleryPhoto;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.GalleryStage;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.GalleryPhotoRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.project.dto.CreateProjectRequest;
import com.inkflow.crm.module.project.dto.ProjectDto;
import com.inkflow.crm.module.project.dto.ProjectFilterRequest;
import com.inkflow.crm.module.project.mapper.ProjectMapper;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.UserPrincipal;
import com.inkflow.crm.support.AuditMocks;
import com.inkflow.crm.module.project.dto.UpdateProjectRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private GalleryPhotoRepository galleryPhotoRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @InjectMocks
    private ProjectService projectService;

    @BeforeEach
    void stubAudit() {
        AuditMocks.stubLabelFormatter(auditLabelFormatter);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createProject_persistsInProgressProject() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        authenticate(tenantId);

        Client client = Client.builder().id(clientId).tenantId(tenantId).build();
        Staff artist = Staff.builder().id(artistId).tenantId(tenantId).build();
        Project saved = Project.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.IN_PROGRESS)
                .client(client)
                .artist(artist)
                .build();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .clientId(clientId)
                .artistId(artistId)
                .title("Sleeve")
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .build();

        when(clientRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));
        when(staffRepository.findByIdAndDeletedAtIsNull(artistId)).thenReturn(Optional.of(artist));
        when(projectRepository.save(any(Project.class))).thenReturn(saved);
        when(galleryPhotoRepository.findByProjectId(saved.getId())).thenReturn(java.util.List.of());
        when(projectMapper.toSessionDtos(saved)).thenReturn(java.util.List.of());
        when(projectMapper.toDto(org.mockito.ArgumentMatchers.eq(saved), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(ProjectDto.builder().id(saved.getId()).title("Sleeve").build());

        ProjectDto result = projectService.createProject(request);

        assertEquals("Sleeve", result.getTitle());
        verify(projectRepository).save(any(Project.class));
    }

    @Test
    void getProjectById_rejectsForeignTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.getProjectById(projectId));
    }

    @Test
    void shouldPersistInProgressDefaultsWhenCreatingProject() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        authenticate(tenantId);

        Client client = Client.builder().id(clientId).tenantId(tenantId).build();
        Staff artist = Staff.builder().id(artistId).tenantId(tenantId).build();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .clientId(clientId)
                .artistId(artistId)
                .title("Sleeve")
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .build();

        when(clientRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));
        when(staffRepository.findByIdAndDeletedAtIsNull(artistId)).thenReturn(Optional.of(artist));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(UUID.randomUUID());
            return project;
        });
        when(galleryPhotoRepository.findByProjectId(any())).thenReturn(java.util.List.of());
        when(projectMapper.toSessionDtos(any())).thenReturn(java.util.List.of());
        when(projectMapper.toDto(any(), any(), any())).thenReturn(ProjectDto.builder().title("Sleeve").build());

        projectService.createProject(request);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project saved = captor.getValue();
        assertEquals(ProjectStatus.IN_PROGRESS, saved.getStatus());
        assertEquals(BigDecimal.ZERO, saved.getTotalPaid());
        assertEquals(0, saved.getCompletedSessions());
        assertEquals(client, saved.getClient());
        assertEquals(artist, saved.getArtist());
    }

    @Test
    void shouldThrowWhenClientNotFoundOnCreate() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        authenticate(tenantId);

        CreateProjectRequest request = CreateProjectRequest.builder()
                .clientId(clientId)
                .artistId(UUID.randomUUID())
                .title("Sleeve")
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .build();

        when(clientRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.createProject(request));
    }

    @Test
    void shouldThrowWhenArtistNotFoundOnCreate() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        authenticate(tenantId);

        Client client = Client.builder().id(clientId).tenantId(tenantId).build();
        CreateProjectRequest request = CreateProjectRequest.builder()
                .clientId(clientId)
                .artistId(artistId)
                .title("Sleeve")
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .build();

        when(clientRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));
        when(staffRepository.findByIdAndDeletedAtIsNull(artistId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.createProject(request));
    }

    @Test
    void shouldTransitionToCompletedWhenStatusUpdated() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .client(testClient())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(galleryPhotoRepository.findByProjectId(projectId)).thenReturn(java.util.List.of());
        when(projectMapper.toSessionDtos(project)).thenReturn(java.util.List.of());
        when(projectMapper.toDto(any(), any(), any())).thenReturn(ProjectDto.builder().status("completed").build());

        projectService.updateProject(projectId, UpdateProjectRequest.builder().status("completed").build());

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertEquals(ProjectStatus.COMPLETED, captor.getValue().getStatus());
    }

    @Test
    void shouldTransitionToArchivedWhenStatusUpdated() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .client(testClient())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(galleryPhotoRepository.findByProjectId(projectId)).thenReturn(java.util.List.of());
        when(projectMapper.toSessionDtos(project)).thenReturn(java.util.List.of());
        when(projectMapper.toDto(any(), any(), any())).thenReturn(ProjectDto.builder().status("archived").build());

        projectService.updateProject(projectId, UpdateProjectRequest.builder().status("archived").build());

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertEquals(ProjectStatus.ARCHIVED, captor.getValue().getStatus());
    }

    @Test
    void shouldThrowWhenArtistNotFoundOnUpdate() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID missingArtistId = UUID.randomUUID();
        authenticate(tenantId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(staffRepository.findByIdAndDeletedAtIsNull(missingArtistId)).thenReturn(Optional.empty());

        UpdateProjectRequest request = UpdateProjectRequest.builder().artistId(missingArtistId).build();
        assertThrows(ResourceNotFoundException.class, () -> projectService.updateProject(projectId, request));
    }

    @Test
    void shouldClearSketchImageWhenBlankStringProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.IN_PROGRESS)
                .sketchImage("https://cdn.example/sketch.png")
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .client(testClient())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(galleryPhotoRepository.findByProjectId(projectId)).thenReturn(java.util.List.of());
        when(projectMapper.toSessionDtos(project)).thenReturn(java.util.List.of());
        when(projectMapper.toDto(any(), any(), any())).thenReturn(ProjectDto.builder().build());

        projectService.updateProject(projectId, UpdateProjectRequest.builder().sketchImage("   ").build());

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertNull(captor.getValue().getSketchImage());
    }

    @Test
    void shouldSoftDeleteProjectOnDelete() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.ARCHIVED)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .client(testClient())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        projectService.deleteProject(projectId);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void shouldRejectDeleteWhenProjectIsNotArchived() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));

        assertThrows(BusinessRuleException.class, () -> projectService.deleteProject(projectId));
        verify(projectRepository, never()).save(any());
    }

    @Test
    void shouldReturnProjectWhenFoundById() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(galleryPhotoRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(projectMapper.toSessionDtos(project)).thenReturn(List.of());
        when(projectMapper.toDto(eq(project), eq(List.of()), eq(List.of())))
                .thenReturn(ProjectDto.builder().id(projectId).title("Sleeve").build());

        ProjectDto result = projectService.getProjectById(projectId);

        assertEquals(projectId, result.getId());
        assertEquals("Sleeve", result.getTitle());
    }

    @Test
    void shouldForceOnlyMineWhenLackingViewAllPermission() {
        UUID tenantId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        authenticateAsArtist(tenantId, artistId);

        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.PROJECTS_VIEW_ALL.getValue()))
                .thenReturn(false);
        when(projectRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(0);
        pageRequest.setSize(20);

        projectService.getAllProjects(pageRequest, new ProjectFilterRequest(), null);

        verify(projectRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldPersistLocationWhenProvidedOnCreate() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticate(tenantId);

        Client client = Client.builder().id(clientId).tenantId(tenantId).build();
        Staff artist = Staff.builder().id(artistId).tenantId(tenantId).build();
        Location location = Location.builder().id(locationId).tenantId(tenantId).build();

        CreateProjectRequest request = CreateProjectRequest.builder()
                .clientId(clientId)
                .artistId(artistId)
                .locationId(locationId)
                .title("Sleeve")
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .build();

        when(clientRepository.findByIdAndDeletedAtIsNull(clientId)).thenReturn(Optional.of(client));
        when(staffRepository.findByIdAndDeletedAtIsNull(artistId)).thenReturn(Optional.of(artist));
        when(locationRepository.findByIdAndDeletedAtIsNull(locationId)).thenReturn(Optional.of(location));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            project.setId(UUID.randomUUID());
            return project;
        });
        when(galleryPhotoRepository.findByProjectId(any())).thenReturn(List.of());
        when(projectMapper.toSessionDtos(any())).thenReturn(List.of());
        when(projectMapper.toDto(any(), any(), any())).thenReturn(ProjectDto.builder().title("Sleeve").build());

        projectService.createProject(request);

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        assertEquals(location, captor.getValue().getLocation());
    }

    @Test
    void shouldUpdateEditableFieldsWhenProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Old Title")
                .description("Old description")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .client(testClient())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(galleryPhotoRepository.findByProjectId(projectId)).thenReturn(List.of());
        when(projectMapper.toSessionDtos(project)).thenReturn(List.of());
        when(projectMapper.toDto(any(), any(), any())).thenReturn(ProjectDto.builder().build());

        projectService.updateProject(projectId, UpdateProjectRequest.builder()
                .title("New Title")
                .description("New description")
                .estimatedCost(BigDecimal.valueOf(8000))
                .totalSessions(5)
                .sketchImage("https://cdn.example/sketch.png")
                .build());

        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        Project updated = captor.getValue();
        assertEquals("New Title", updated.getTitle());
        assertEquals("New description", updated.getDescription());
        assertEquals(BigDecimal.valueOf(8000), updated.getEstimatedCost());
        assertEquals(5, updated.getTotalSessions());
        assertEquals("https://cdn.example/sketch.png", updated.getSketchImage());
    }

    @Test
    void shouldThrowWhenProjectNotFoundOnUpdate() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> projectService.updateProject(projectId, UpdateProjectRequest.builder().title("New").build()));
    }

    @Test
    void shouldThrowWhenProjectNotFoundOnDelete() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId);

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.deleteProject(projectId));
    }

    @Test
    void shouldPersistGalleryPhotoWhenAddingPhoto() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        authenticate(tenantId, userId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .client(testClient())
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(galleryPhotoRepository.save(any(GalleryPhoto.class))).thenAnswer(invocation -> {
            GalleryPhoto photo = invocation.getArgument(0);
            photo.setId(UUID.randomUUID());
            return photo;
        });
        when(projectMapper.toPhotoDto(any(GalleryPhoto.class)))
                .thenReturn(ProjectDto.PhotoDto.builder().url("https://cdn.example/photo.jpg").stage("sketch").build());

        ProjectDto.PhotoDto result = projectService.addPhoto(projectId, "https://cdn.example/photo.jpg", "sketch");

        ArgumentCaptor<GalleryPhoto> captor = ArgumentCaptor.forClass(GalleryPhoto.class);
        verify(galleryPhotoRepository).save(captor.capture());
        GalleryPhoto savedPhoto = captor.getValue();
        assertEquals(GalleryStage.SKETCH, savedPhoto.getStage());
        assertEquals(userId, savedPhoto.getUploadedBy());
        assertEquals(project, savedPhoto.getProject());
        assertEquals("https://cdn.example/photo.jpg", result.getUrl());
    }

    @Test
    void shouldDeletePhotoFromProject() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        authenticate(tenantId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .client(testClient())
                .build();
        GalleryPhoto photo = GalleryPhoto.builder().id(photoId).tenantId(tenantId).project(project).build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(galleryPhotoRepository.findById(photoId)).thenReturn(Optional.of(photo));

        projectService.deletePhoto(projectId, photoId);

        verify(galleryPhotoRepository).delete(photo);
    }

    @Test
    void shouldThrowWhenPhotoNotFoundOnDelete() {
        UUID tenantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        authenticate(tenantId);

        Project project = Project.builder()
                .id(projectId)
                .tenantId(tenantId)
                .title("Sleeve")
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(BigDecimal.valueOf(5000))
                .totalSessions(3)
                .build();

        when(projectRepository.findByIdAndDeletedAtIsNull(projectId)).thenReturn(Optional.of(project));
        when(galleryPhotoRepository.findById(photoId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> projectService.deletePhoto(projectId, photoId));
    }

    private Client testClient() {
        return Client.builder().id(UUID.randomUUID()).build();
    }

    private void authenticate(UUID tenantId) {
        authenticate(tenantId, UUID.randomUUID());
    }

    private void authenticate(UUID tenantId, UUID userId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void authenticateAsArtist(UUID tenantId, UUID userId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(UserRole.ARTIST)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
