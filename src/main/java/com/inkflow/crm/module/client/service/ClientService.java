package com.inkflow.crm.module.client.service;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.dto.PageResult;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.common.util.PhoneUtils;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.ProjectStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.ClientSpecifications;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.module.client.dto.*;
import com.inkflow.crm.module.client.mapper.ClientMapper;
import com.inkflow.crm.module.project.dto.ProjectSummaryDto;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    public static final int RECENT_CLIENTS_LIMIT = 10;
    private static final int LOST_CLIENT_DAYS = 90;

    private final ClientRepository clientRepository;
    private final AppointmentRepository appointmentRepository;
    private final ProjectRepository projectRepository;
    private final ClientMapper clientMapper;
    private final RolePermissionService rolePermissionService;

    @Transactional(readOnly = true)
    public PageResult<ClientDto> getAllClients(PageRequest pageRequest, ClientFilterRequest filter) {
        Page<Client> page = getClientsPage(pageRequest, filter);
        return new PageResult<>(clientMapper.toDtoList(page.getContent()), PaginationDto.from(page));
    }

    @Transactional(readOnly = true)
    public List<ClientDto> getRecentClients() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID artistId = resolveRecentClientsArtistScope();

        List<UUID> clientIds = appointmentRepository.findRecentClientIds(
                tenantId,
                artistId,
                org.springframework.data.domain.PageRequest.of(0, RECENT_CLIENTS_LIMIT)
        );

        if (clientIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Client> clientsById = clientRepository
                .findByIdInAndTenantIdAndDeletedAtIsNull(clientIds, tenantId)
                .stream()
                .collect(Collectors.toMap(Client::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));

        return clientIds.stream()
                .map(clientsById::get)
                .filter(client -> client != null && !client.isBlacklisted())
                .map(clientMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientDetailDto getClientById(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        Client client = clientRepository.findByIdWithCollections(id, tenantId)
                .orElseThrow(() -> ResourceNotFoundException.client(id.toString()));

        List<Project> activeProjects = projectRepository.findByClientIdAndStatusInAndDeletedAtIsNull(
                id, List.of(ProjectStatus.IN_PROGRESS));

        return buildDetailDto(client, activeProjects);
    }

    @Transactional(readOnly = true)
    public ClientDto findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return clientRepository.findByEmailIgnoreCaseAndTenantIdAndDeletedAtIsNull(email.trim(), tenantId)
                .map(clientMapper::toDto)
                .orElse(null);
    }

    @Transactional
    public ClientDto createClient(CreateClientRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (clientRepository.existsByEmailIgnoreCaseAndTenantIdAndDeletedAtIsNull(normalizedEmail, tenantId)) {
            throw BusinessRuleException.emailAlreadyExists(normalizedEmail);
        }

        String normalizedPhone = normalizeOptionalPhone(request.getPhone());
        if (normalizedPhone != null
                && clientRepository.existsByPhoneAndTenantIdAndDeletedAtIsNull(normalizedPhone, tenantId)) {
            throw BusinessRuleException.phoneAlreadyExists(normalizedPhone);
        }

        Client client = clientMapper.toEntity(request);
        client.setTenantId(tenantId);
        client.setEmail(normalizedEmail);
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
            if (request.getPhone().isBlank()) {
                client.setPhone(null);
            } else {
                String normalizedPhone = PhoneUtils.normalize(request.getPhone());
                if (!normalizedPhone.equals(client.getPhone())
                        && clientRepository.existsByPhoneAndTenantIdAndDeletedAtIsNull(normalizedPhone, tenantId)) {
                    throw BusinessRuleException.phoneAlreadyExists(normalizedPhone);
                }
                client.setPhone(normalizedPhone);
            }
            request.setPhone(null);
        }

        if (request.getEmail() != null) {
            if (request.getEmail().isBlank()) {
                throw BusinessRuleException.emailRequired();
            }

            String normalizedEmail = normalizeEmail(request.getEmail());
            if (client.getEmail() == null || !normalizedEmail.equalsIgnoreCase(client.getEmail())) {
                if (clientRepository.existsByEmailIgnoreCaseAndTenantIdAndDeletedAtIsNull(normalizedEmail, tenantId)) {
                    throw BusinessRuleException.emailAlreadyExists(normalizedEmail);
                }
            }
            client.setEmail(normalizedEmail);
            request.setEmail(null);
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
                clientId, List.of(ProjectStatus.IN_PROGRESS)));
    }

    private Page<Client> getClientsPage(PageRequest pageRequest, ClientFilterRequest filter) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        ClientFilterRequest effectiveFilter = filter != null ? filter : new ClientFilterRequest();

        if (!rolePermissionService.hasPermission(tenantId, SecurityUtils.getCurrentUserRole(), Permission.CLIENTS_VIEW_ALL.getValue())) {
            effectiveFilter.setOnlyMine(true);
        }

        UUID artistId = resolveArtistId(effectiveFilter);
        Instant lostCutoff = Boolean.TRUE.equals(effectiveFilter.getLost())
                ? Instant.now().minus(LOST_CLIENT_DAYS, ChronoUnit.DAYS)
                : null;

        Specification<Client> spec = Specification
                .where(ClientSpecifications.belongsToTenant(tenantId))
                .and(ClientSpecifications.notDeleted())
                .and(ClientSpecifications.searchLike(effectiveFilter.getSearch()))
                .and(ClientSpecifications.dormantIs(effectiveFilter.getDormant()))
                .and(ClientSpecifications.blacklisted(effectiveFilter.getBlacklisted()))
                .and(ClientSpecifications.excludeBlacklisted(effectiveFilter.getExcludeBlacklisted()))
                .and(ClientSpecifications.totalVisitsBetween(effectiveFilter.getTotalVisitsMin(), effectiveFilter.getTotalVisitsMax()))
                .and(ClientSpecifications.cancelledVisitsBetween(effectiveFilter.getCancelledVisitsMin(), effectiveFilter.getCancelledVisitsMax()))
                .and(ClientSpecifications.balanceBetween(effectiveFilter.getBalanceMin(), effectiveFilter.getBalanceMax()))
                .and(ClientSpecifications.ltvBetween(effectiveFilter.getLtvMin(), effectiveFilter.getLtvMax()))
                .and(ClientSpecifications.avgCheckBetween(effectiveFilter.getAvgCheckMin(), effectiveFilter.getAvgCheckMax()))
                .and(ClientSpecifications.createdAtBetween(effectiveFilter.getCreatedAtFrom(), effectiveFilter.getCreatedAtTo()))
                .and(ClientSpecifications.lastVisitBetween(effectiveFilter.getLastVisitFrom(), effectiveFilter.getLastVisitTo()))
                .and(ClientSpecifications.firstVisitBetween(effectiveFilter.getFirstVisitFrom(), effectiveFilter.getFirstVisitTo()))
                .and(ClientSpecifications.birthdayBetween(effectiveFilter.getBirthdayFrom(), effectiveFilter.getBirthdayTo()))
                .and(ClientSpecifications.hasTags(effectiveFilter.getTags()))
                .and(ClientSpecifications.workedWithArtist(artistId))
                .and(ClientSpecifications.visitedServices(effectiveFilter.getServiceIds()))
                .and(ClientSpecifications.hasActiveAppointments(
                        effectiveFilter.getHasActiveAppointments(),
                        effectiveFilter.getActiveAppointmentFrom(),
                        effectiveFilter.getActiveAppointmentTo()))
                .and(ClientSpecifications.lostSince(lostCutoff));

        return clientRepository.findAll(spec, pageRequest.toPageable());
    }

    private UUID resolveRecentClientsArtistScope() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        if (rolePermissionService.hasPermission(tenantId, SecurityUtils.getCurrentUserRole(), Permission.CLIENTS_VIEW_ALL.getValue())) {
            return null;
        }
        return SecurityUtils.getCurrentUserId();
    }

    private UUID resolveArtistId(ClientFilterRequest filter) {
        if (filter.getArtistId() != null) {
            return filter.getArtistId();
        }
        return Boolean.TRUE.equals(filter.getOnlyMine()) ? SecurityUtils.getCurrentUserId() : null;
    }

    Client requireClient(UUID tenantId, UUID id) {
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
                .whatsapp(client.getWhatsapp())
                .facebook(client.getFacebook())
                .tags(new ArrayList<>(client.getTags()))
                .medicalConditions(new ArrayList<>(client.getMedicalConditions()))
                .source(client.getSource() != null ? client.getSource().getValue() : null)
                .dormant(client.isDormant())
                .blacklisted(client.isBlacklisted())
                .notes(client.getNotes())
                .lastVisit(client.getLastVisit())
                .firstVisit(client.getFirstVisit())
                .totalVisits(client.getTotalVisits())
                .cancelledVisits(client.getCancelledVisits())
                .ltv(client.getLtv())
                .balance(client.getBalance())
                .activeProjects(toProjectSummaries(activeProjects))
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private static String normalizeOptionalPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String normalized = PhoneUtils.normalize(phone);
        return normalized.isBlank() ? null : normalized;
    }
}
