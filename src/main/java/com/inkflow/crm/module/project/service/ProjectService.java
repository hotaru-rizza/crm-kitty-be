package com.inkflow.crm.module.project.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.client.dto.ClientSummaryDto;
import com.inkflow.crm.module.project.dto.*;
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

    @Transactional(readOnly = true)
    public List<ProjectDto> getAllProjects(PageRequest pageRequest, String status) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Project> page;

        if (status != null) {
            page = projectRepository.findByTenantIdAndStatusAndDeletedAtIsNull(
                    tenantId, ProjectStatus.fromValue(status), pageRequest.toPageable());
        } else {
            page = projectRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageRequest.toPageable());
        }

        return page.getContent().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public PaginationDto getPagination(PageRequest pageRequest, String status) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Page<Project> page;

        if (status != null) {
            page = projectRepository.findByTenantIdAndStatusAndDeletedAtIsNull(
                    tenantId, ProjectStatus.fromValue(status), pageRequest.toPageable());
        } else {
            page = projectRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageRequest.toPageable());
        }

        return PaginationDto.from(page);
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

        Project project = Project.builder()
                .tenantId(tenantId)
                .title(request.getTitle())
                .description(request.getDescription())
                .client(client)
                .artist(artist)
                .status(ProjectStatus.IN_PROGRESS)
                .estimatedCost(request.getEstimatedCost())
                .totalPaid(BigDecimal.ZERO)
                .totalSessions(request.getTotalSessions())
                .completedSessions(0)
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

    private ProjectDto mapToDto(Project project) {
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
                .createdAt(project.getCreatedAt())
                .build();
    }
}
