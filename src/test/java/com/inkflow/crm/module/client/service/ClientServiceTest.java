package com.inkflow.crm.module.client.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.module.client.dto.ClientDto;
import com.inkflow.crm.module.client.dto.CreateClientRequest;
import com.inkflow.crm.module.client.dto.UpdateClientRequest;
import com.inkflow.crm.module.client.mapper.ClientMapper;
import com.inkflow.crm.module.client.support.ClientAccessGuard;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ClientMapper clientMapper;

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private ClientBalanceService clientBalanceService;

    @Mock
    private ClientStatsService clientStatsService;

    @Mock
    private ClientAccessGuard clientAccessGuard;

    @InjectMocks
    private ClientService clientService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createClient_normalizesPhoneAndPersists() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        CreateClientRequest request = CreateClientRequest.builder()
                .firstName("Anna")
                .lastName("Koval")
                .email("anna.koval@test.com")
                .phone("+38 (099) 123-45-67")
                .build();

        Client entity = Client.builder()
                .firstName("Anna")
                .lastName("Koval")
                .email("anna.koval@test.com")
                .build();
        Client saved = Client.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName("Anna")
                .lastName("Koval")
                .email("anna.koval@test.com")
                .phone("+380991234567")
                .build();

        when(clientRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull("anna.koval@test.com")).thenReturn(false);
        when(clientRepository.existsByPhoneAndDeletedAtIsNull("+380991234567")).thenReturn(false);
        when(clientMapper.toEntity(request)).thenReturn(entity);
        when(clientRepository.save(entity)).thenReturn(saved);
        when(clientMapper.toDto(saved)).thenReturn(ClientDto.builder().id(saved.getId()).phone("+380991234567").build());

        ClientDto result = clientService.createClient(request);

        assertEquals("+380991234567", saved.getPhone());
        assertEquals("+380991234567", result.getPhone());
        assertEquals(tenantId, saved.getTenantId());
    }

    @Test
    void createClient_rejectsDuplicatePhone() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        CreateClientRequest request = CreateClientRequest.builder()
                .firstName("Anna")
                .lastName("Koval")
                .email("anna.koval@test.com")
                .phone("+380991234567")
                .build();

        when(clientRepository.existsByPhoneAndDeletedAtIsNull("+380991234567")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> clientService.createClient(request));
    }

    @Test
    void updateClient_rejectsDuplicatePhoneWhenChanging() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        authenticate(tenantId);

        Client client = Client.builder()
                .id(clientId)
                .tenantId(tenantId)
                .email("existing@test.com")
                .phone("+380991234567")
                .build();

        when(clientRepository.findByIdAndDeletedAtIsNull(clientId))
                .thenReturn(Optional.of(client));
        when(clientRepository.existsByPhoneAndDeletedAtIsNull("+380999999999"))
                .thenReturn(true);

        UpdateClientRequest request = UpdateClientRequest.builder()
                .phone("+380999999999")
                .build();

        assertThrows(BusinessRuleException.class, () -> clientService.updateClient(clientId, request));
    }

    @Test
    void getAllClients_forcesOnlyMineWhenLackingViewAllPermission() {
        UUID tenantId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        authenticateAsArtist(tenantId, artistId);

        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CLIENTS_VIEW_ALL.getValue()))
                .thenReturn(false);
        when(clientRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(clientMapper.toDtoList(any())).thenReturn(List.of());

        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(0);
        pageRequest.setSize(20);

        clientService.getAllClients(pageRequest, new com.inkflow.crm.module.client.dto.ClientFilterRequest());

        verify(clientRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void deleteClient_softDeletesTenantScopedClient() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        authenticate(tenantId);

        Client client = Client.builder()
                .id(clientId)
                .tenantId(tenantId)
                .email("existing@test.com")
                .phone("+380991234567")
                .build();

        when(clientRepository.findByIdAndDeletedAtIsNull(clientId))
                .thenReturn(Optional.of(client));

        clientService.deleteClient(clientId);

        verify(clientRepository).save(client);
    }

    @Test
    void getClientById_rejectsForeignTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        authenticate(tenantId);

        when(clientRepository.findByIdWithCollections(clientId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> clientService.getClientById(clientId));
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

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
