package com.inkflow.crm.module.project.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.module.project.dto.*;
import com.inkflow.crm.module.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.inkflow.crm.security.RequirePermission;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    @RequirePermission({"projects.view_all", "projects.view_own"})
    public ResponseEntity<ApiResponse<List<ProjectDto>>> getAllProjects(
            @ModelAttribute PageRequest pageRequest,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) List<UUID> artistId,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean onlyMine,
            @RequestParam(required = false) UUID locationId) {
        PageResult<ProjectDto> result = projectService.getAllProjects(pageRequest, status, artistId, clientId, search, onlyMine, locationId);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), result.getPagination()));
    }

    @GetMapping("/{id}")
    @RequirePermission({"projects.view_all", "projects.view_own"})
    public ResponseEntity<ApiResponse<ProjectDto>> getProject(@PathVariable UUID id) {
        ProjectDto project = projectService.getProjectById(id);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @PostMapping
    @RequirePermission("projects.create")
    public ResponseEntity<ApiResponse<ProjectDto>> createProject(@Valid @RequestBody CreateProjectRequest request) {
        ProjectDto project = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(project));
    }

    @PatchMapping("/{id}")
    @RequirePermission("projects.edit")
    public ResponseEntity<ApiResponse<ProjectDto>> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request) {
        ProjectDto project = projectService.updateProject(id, request);
        return ResponseEntity.ok(ApiResponse.success(project));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("projects.delete")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable UUID id) {
        projectService.deleteProject(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PostMapping("/{id}/photos")
    @RequirePermission("projects.edit")
    public ResponseEntity<ApiResponse<ProjectDto.PhotoDto>> addPhoto(
            @PathVariable UUID id,
            @RequestBody AddPhotoRequest request) {
        ProjectDto.PhotoDto photo = projectService.addPhoto(id, request.url(), request.stage());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(photo));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @RequirePermission("projects.edit")
    public ResponseEntity<ApiResponse<Void>> deletePhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId) {
        projectService.deletePhoto(id, photoId);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    record AddPhotoRequest(String url, String stage) {}
}
