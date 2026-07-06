package com.inkflow.crm.module.client.support;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.ClientSpecifications;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ClientAccessGuard {

    private final RolePermissionService rolePermissionService;
    private final ClientRepository clientRepository;

    public void requireView(Client client) {
        if (!canView(client)) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }

    public void requireView(UUID clientId) {
        if (!canView(clientId)) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }

    private boolean canView(Client client) {
        return canView(client.getId());
    }

    private boolean canView(UUID clientId) {
        if (SecurityUtils.getCurrentUserRole() == UserRole.OWNER) {
            return true;
        }
        if (hasClientsViewAll()) {
            return true;
        }
        return hasClientsViewOwn() && hasWorkedWithCurrentArtist(clientId);
    }

    private boolean hasWorkedWithCurrentArtist(UUID clientId) {
        UUID artistId = SecurityUtils.getCurrentUserId();
        Specification<Client> spec = Specification.where(ClientSpecifications.notDeleted())
                .and((root, query, cb) -> cb.equal(root.get("id"), clientId))
                .and(ClientSpecifications.workedWithArtist(artistId));
        return clientRepository.exists(spec);
    }

    private boolean hasClientsViewAll() {
        return hasPermission(Permission.CLIENTS_VIEW_ALL);
    }

    private boolean hasClientsViewOwn() {
        return hasPermission(Permission.CLIENTS_VIEW_OWN);
    }

    private boolean hasPermission(Permission permission) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UserRole role = SecurityUtils.getCurrentUserRole();
        return rolePermissionService.hasPermission(tenantId, role, permission.getValue());
    }
}
