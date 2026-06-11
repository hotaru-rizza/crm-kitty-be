package com.inkflow.crm.module.client.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.common.util.PhoneUtils;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.ClientStatus;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.module.client.dto.*;
import com.inkflow.crm.module.client.mapper.ClientMapper;
import com.inkflow.crm.module.project.dto.ProjectSummaryDto;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private static final int LOST_CLIENT_DAYS = 90;

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final ClientMapper clientMapper;
    private final RolePermissionService rolePermissionService;

    @Transactional(readOnly = true)
    public PageResult<ClientDto> getAllClients(PageRequest pageRequest, String search, String status, Boolean onlyMine, Boolean lost) {
        Page<Client> page = getClientsPage(pageRequest, search, status, onlyMine, lost);
        return new PageResult<>(clientMapper.toDtoList(page.getContent()), PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public ClientDetailDto getClientById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Client client = clientRepository.findByIdWithCollections(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(id.toString()));

        List<Project> activeProjects = projectRepository.findByClientIdAndStatusInAndDeletedAtIsNull(
                id, List.of(ProjectStatus.IN_PROGRESS, ProjectStatus.ON_HOLD));

        return buildDetailDto(client, activeProjects);
    }

    @Transactional
    public ClientDto createClient(CreateClientRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String normalizedPhone = PhoneUtils.normalize(request.getPhone());

        if (clientRepository.existsByPhoneAndTenantIdAndDeletedAtIsNull(normalizedPhone, tenantId)) {
            throw BusinessRuleException.phoneAlreadyExists(normalizedPhone);
        }

        Client client = clientMapper.toEntity(request);
        client.setTenantId(tenantId);
        client.setPhone(normalizedPhone);
        client = clientRepository.save(client);

        log.info("Client created: tenantId={} clientId={}", tenantId, client.getId());
        return clientMapper.toDto(client);
    }

    @Transactional
    public ClientDto updateClient(UUID id, UpdateClientRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Client client = requireClient(tenantId, id);

        if (request.getPhone() != null) {
            String normalizedPhone = PhoneUtils.normalize(request.getPhone());
            if (!normalizedPhone.equals(client.getPhone())
                    && clientRepository.existsByPhoneAndTenantIdAndDeletedAtIsNull(normalizedPhone, tenantId)) {
                throw BusinessRuleException.phoneAlreadyExists(normalizedPhone);
            }
            request.setPhone(normalizedPhone);
        }

        clientMapper.updateEntity(request, client);
        client = clientRepository.save(client);

        log.info("Client updated: tenantId={} clientId={}", tenantId, id);
        return clientMapper.toDto(client);
    }

    @Transactional
    public void deleteClient(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Client client = requireClient(tenantId, id);

        client.softDelete();
        clientRepository.save(client);
        log.info("Client deleted: tenantId={} clientId={}", tenantId, id);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> getClientProjects(UUID clientId) {
        requireClient(SecurityUtils.getCurrentTenantId(), clientId);
        return toProjectSummaries(projectRepository.findByClientIdAndDeletedAtIsNull(clientId));
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> getClientActiveProjects(UUID clientId) {
        requireClient(SecurityUtils.getCurrentTenantId(), clientId);
        return toProjectSummaries(projectRepository.findByClientIdAndStatusInAndDeletedAtIsNull(
                clientId, List.of(ProjectStatus.IN_PROGRESS, ProjectStatus.ON_HOLD)));
    }

    private Page<Client> getClientsPage(PageRequest pageRequest, String search, String status, Boolean onlyMine, Boolean lost) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        ClientStatus clientStatus = status != null ? ClientStatus.fromValue(status) : null;

        if (!rolePermissionService.hasPermission(tenantId, SecurityUtils.getCurrentUserRole(), Permission.CLIENTS_VIEW_ALL.getValue())) {
            onlyMine = true;
        }

        UUID artistId = Boolean.TRUE.equals(onlyMine) ? SecurityUtils.getCurrentUserId() : null;

        if (Boolean.TRUE.equals(lost)) {
            Instant cutoff = Instant.now().minus(LOST_CLIENT_DAYS, ChronoUnit.DAYS);
            return clientRepository.findLostClients(tenantId, cutoff, search, artistId, pageRequest.toPageable());
        }

        return clientRepository.findWithFilters(tenantId, search, clientStatus, artistId, pageRequest.toPageable());
    }

    private Client requireClient(UUID tenantId, UUID id) {
        return clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(id.toString()));
    }

    private List<ProjectSummaryDto> toProjectSummaries(List<Project> projects) {
        return projects.stream()
                .map(p -> ProjectSummaryDto.builder().id(p.getId()).title(p.getTitle()).build())
                .toList();
    }

    private ClientDetailDto buildDetailDto(Client client, List<Project> activeProjects) {
        return ClientDetailDto.builder()
                .id(client.getId())
                .firstName(client.getFirstName())
                .lastName(client.getLastName())
                .phone(client.getPhone())
                .email(client.getEmail())
                .avatar(client.getAvatar())
                .birthDate(client.getBirthDate())
                .instagram(client.getInstagram())
                .telegram(client.getTelegram())
                .tags(new ArrayList<>(client.getTags()))
                .medicalConditions(new ArrayList<>(client.getMedicalConditions()))
                .source(client.getSource() != null ? client.getSource().getValue() : null)
                .status(client.getStatus().getValue())
                .notes(client.getNotes())
                .lastVisit(client.getLastVisit())
                .totalVisits(client.getTotalVisits())
                .cancelledVisits(client.getCancelledVisits())
                .ltv(client.getLtv())
                .activeProjects(toProjectSummaries(activeProjects))
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }
}
