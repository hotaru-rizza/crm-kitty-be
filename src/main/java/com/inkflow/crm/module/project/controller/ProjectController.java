package com.inkflow.crm.module.project.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.project.dto.*;
import com.inkflow.crm.module.project.service.ProjectService;
import com.inkflow.crm.security.RequirePermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @RequirePermission({Permission.PROJECTS_VIEW_ALL, Permission.PROJECTS_VIEW_OWN})
    public ResponseEntity<ApiResponse<List<ProjectDto>>> getAllProjects(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) List<UUID> artistId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean onlyMine,
            @RequestParam(required = false) UUID locationId) {
        PageResult<ProjectDto> result = projectService.getAllProjects(
                pageRequest, status, artistId, clientId, search, onlyMine, locationId);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/{id}")
    @RequirePermission({Permission.PROJECTS_VIEW_ALL, Permission.PROJECTS_VIEW_OWN})
    public ResponseEntity<ApiResponse<ProjectDto>> getProject(@PathVariable UUID id) {
        ProjectDto project = projectService.getProjectById(id);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @PostMapping
    @RequirePermission(Permission.PROJECTS_CREATE)
    public ResponseEntity<ApiResponse<ProjectDto>> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectDto project = projectService.createProject(request);
        log.info("Project created via API: projectId={}", project.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(project));
    }

    @PatchMapping("/{id}")
    @RequirePermission(Permission.PROJECTS_EDIT)
    public ResponseEntity<ApiResponse<ProjectDto>> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request) {
        ProjectDto project = projectService.updateProject(id, request);
        log.info("Project updated via API: projectId={}", id);

        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @DeleteMapping("/{id}")
    @RequirePermission(Permission.PROJECTS_DELETE)
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        log.info("Project deleted via API: projectId={}", id);

        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/{id}/photos")
    @RequirePermission(Permission.PROJECTS_EDIT)
    public ResponseEntity<ApiResponse<ProjectDto.PhotoDto>> addPhoto(
            @PathVariable UUID id,
            @Valid @RequestBody AddProjectPhotoRequest request) {
        ProjectDto.PhotoDto photo = projectService.addPhoto(id, request.url(), request.stage());
        log.info("Project photo added via API: projectId={} photoId={}", id, photo.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(photo));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @RequirePermission(Permission.PROJECTS_EDIT)
    public ResponseEntity<ApiResponse<Void>> deletePhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId) {
        projectService.deletePhoto(id, photoId);
        log.info("Project photo deleted via API: projectId={} photoId={}", id, photoId);

        return ResponseEntity.ok(ApiResponse.empty());
    }
}
