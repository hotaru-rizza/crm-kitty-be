package com.inkflow.crm.module.client.support;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientAccessGuardTest {

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private ClientRepository clientRepository;

    @InjectMocks
    private ClientAccessGuard accessGuard;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID artistAId = UUID.randomUUID();
    private final UUID clientId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void requireView_allowsOwnerForAnyClient() {
        Client client = Client.builder().id(clientId).build();
        SecurityTestSupport.authenticate(UUID.randomUUID(), tenantId, UserRole.OWNER);

        assertDoesNotThrow(() -> accessGuard.requireView(client));
    }

    @Test
    void requireView_allowsAdminWithViewAll() {
        Client client = Client.builder().id(clientId).build();
        SecurityTestSupport.authenticate(UUID.randomUUID(), tenantId, UserRole.ADMIN);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ADMIN, Permission.CLIENTS_VIEW_ALL.getValue()))
                .thenReturn(true);

        assertDoesNotThrow(() -> accessGuard.requireView(client));
    }

    @Test
    void requireView_allowsArtistWhenWorkedWithClient() {
        Client client = Client.builder().id(clientId).build();
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CLIENTS_VIEW_ALL.getValue()))
                .thenReturn(false);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CLIENTS_VIEW_OWN.getValue()))
                .thenReturn(true);
        when(clientRepository.exists(any(Specification.class))).thenReturn(true);

        assertDoesNotThrow(() -> accessGuard.requireView(client));
    }

    @Test
    void requireView_deniesArtistWhenNeverWorkedWithClient() {
        Client client = Client.builder().id(clientId).build();
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CLIENTS_VIEW_ALL.getValue()))
                .thenReturn(false);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.CLIENTS_VIEW_OWN.getValue()))
                .thenReturn(true);
        when(clientRepository.exists(any(Specification.class))).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireView(client));
    }

    @Test
    void requireView_deniesWhenNoClientPermissions() {
        Client client = Client.builder().id(clientId).build();
        SecurityTestSupport.authenticate(artistAId, tenantId, UserRole.ARTIST);
        when(rolePermissionService.hasPermission(eq(tenantId), eq(UserRole.ARTIST), eq(Permission.CLIENTS_VIEW_ALL.getValue())))
                .thenReturn(false);
        when(rolePermissionService.hasPermission(eq(tenantId), eq(UserRole.ARTIST), eq(Permission.CLIENTS_VIEW_OWN.getValue())))
                .thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> accessGuard.requireView(client));
    }
}
