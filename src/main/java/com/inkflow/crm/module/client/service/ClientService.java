package com.inkflow.crm.module.client.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.ClientStatus;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.module.client.dto.*;
import com.inkflow.crm.module.client.mapper.ClientMapper;
import com.inkflow.crm.module.project.dto.ProjectSummaryDto;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final ClientMapper clientMapper;

    @Transactional(readOnly = true)
    public List<ClientDto> getAllClients(PageRequest pageRequest, String search, String status, Boolean onlyMine) {
        Page<Client> page = getClientsPage(pageRequest, search, status, onlyMine);
        return clientMapper.toDtoList(page.getContent());
    }

    @Transactional(readOnly = true)
    public PaginationDto getPagination(PageRequest pageRequest, String search, String status, Boolean onlyMine) {
        Page<Client> page = getClientsPage(pageRequest, search, status, onlyMine);
        return PaginationDto.from(page);
    }

    private Page<Client> getClientsPage(PageRequest pageRequest, String search, String status, Boolean onlyMine) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        ClientStatus clientStatus = status != null ? ClientStatus.fromValue(status) : null;
        
        // If onlyMine is true, get current user's ID to filter clients who have projects with this artist
        UUID artistId = Boolean.TRUE.equals(onlyMine) ? SecurityUtils.getCurrentUserId() : null;

        return clientRepository.findWithFilters(tenantId, search, clientStatus, artistId, pageRequest.toPageable());
    }

    @Transactional(readOnly = true)
    public ClientDetailDto getClientById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Client client = clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(id.toString()));

        List<Project> activeProjects = projectRepository.findByClientIdAndStatusInAndDeletedAtIsNull(
                id, List.of(ProjectStatus.IN_PROGRESS, ProjectStatus.ON_HOLD));

        return buildDetailDto(client, activeProjects);
    }

    @Transactional
    public ClientDto createClient(CreateClientRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        String normalizedPhone = normalizePhone(request.getPhone());
        if (clientRepository.existsByPhoneAndTenantIdAndDeletedAtIsNull(normalizedPhone, tenantId)) {
            throw BusinessRuleException.phoneAlreadyExists(normalizedPhone);
        }

        Client client = clientMapper.toEntity(request);
        client.setTenantId(tenantId);
        client.setPhone(normalizedPhone);

        client = clientRepository.save(client);
        return clientMapper.toDto(client);
    }

    @Transactional
    public ClientDto updateClient(UUID id, UpdateClientRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Client client = clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(id.toString()));

        if (request.getPhone() != null) {
            String normalizedPhone = normalizePhone(request.getPhone());
            if (!normalizedPhone.equals(client.getPhone()) &&
                clientRepository.existsByPhoneAndTenantIdAndDeletedAtIsNull(normalizedPhone, tenantId)) {
                throw BusinessRuleException.phoneAlreadyExists(normalizedPhone);
            }
            request.setPhone(normalizedPhone);
        }

        clientMapper.updateEntity(request, client);
        client = clientRepository.save(client);
        return clientMapper.toDto(client);
    }

    @Transactional
    public void deleteClient(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Client client = clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(id.toString()));

        client.softDelete();
        clientRepository.save(client);
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> getClientProjects(UUID clientId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(clientId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(clientId.toString()));

        return projectRepository.findByClientIdAndDeletedAtIsNull(clientId).stream()
                .map(p -> ProjectSummaryDto.builder()
                        .id(p.getId())
                        .title(p.getTitle())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryDto> getClientActiveProjects(UUID clientId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(clientId, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(clientId.toString()));

        return projectRepository.findByClientIdAndStatusInAndDeletedAtIsNull(
                clientId, List.of(ProjectStatus.IN_PROGRESS, ProjectStatus.ON_HOLD)).stream()
                .map(p -> ProjectSummaryDto.builder()
                        .id(p.getId())
                        .title(p.getTitle())
                        .build())
                .collect(Collectors.toList());
    }

    private String normalizePhone(String phone) {
        return phone.replaceAll("[^0-9+]", "");
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
                .tags(client.getTags())
                .medicalConditions(client.getMedicalConditions())
                .source(client.getSource() != null ? client.getSource().getValue() : null)
                .status(client.getStatus().getValue())
                .notes(client.getNotes())
                .lastVisit(client.getLastVisit())
                .totalVisits(client.getTotalVisits())
                .cancelledVisits(client.getCancelledVisits())
                .ltv(client.getLtv())
                .activeProjects(activeProjects.stream()
                        .map(p -> ProjectSummaryDto.builder()
                                .id(p.getId())
                                .title(p.getTitle())
                                .build())
                        .collect(Collectors.toList()))
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }
}
