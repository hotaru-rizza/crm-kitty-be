package com.inkflow.crm.security;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.domain.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnAuthenticatedTenantUserAndRole() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticate(userId, tenantId, UserRole.OWNER);

        assertEquals(tenantId, SecurityUtils.getCurrentTenantId());
        assertEquals(userId, SecurityUtils.getCurrentUserId());
        assertEquals(UserRole.OWNER, SecurityUtils.getCurrentUserRole());
        assertEquals(userId, SecurityUtils.getCurrentUser().getId());
    }

    @Test
    void shouldReturnNullUserWhenNotAuthenticated() {
        assertNull(SecurityUtils.getCurrentUser());
    }

    @Test
    void shouldReturnNullUserWhenPrincipalIsNotUserPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymous", null)
        );

        assertNull(SecurityUtils.getCurrentUser());
    }

    @Test
    void shouldThrowUnauthorizedWhenUserMissing() {
        assertThrows(ApiException.class, SecurityUtils::getCurrentUserOrThrow);
    }

    @Test
    void shouldRejectArtistForAdminAccess() {
        authenticate(UUID.randomUUID(), UUID.randomUUID(), UserRole.ARTIST);

        assertThrows(AccessDeniedException.class, SecurityUtils::requireAdminAccess);
    }

    @Test
    void shouldAllowOwnerAndAdminForAdminAccess() {
        authenticate(UUID.randomUUID(), UUID.randomUUID(), UserRole.OWNER);
        SecurityUtils.requireAdminAccess();

        authenticate(UUID.randomUUID(), UUID.randomUUID(), UserRole.ADMIN);
        SecurityUtils.requireAdminAccess();
    }

    @Test
    void shouldRejectNonOwnerForOwnerAccess() {
        authenticate(UUID.randomUUID(), UUID.randomUUID(), UserRole.ADMIN);

        assertThrows(AccessDeniedException.class, SecurityUtils::requireOwner);
    }

    @Test
    void shouldAllowOwnerForOwnerAccess() {
        authenticate(UUID.randomUUID(), UUID.randomUUID(), UserRole.OWNER);

        SecurityUtils.requireOwner();
    }

    @Test
    void shouldRejectArtistWhenLocationNotAssigned() {
        UUID locationId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .role(UserRole.ARTIST)
                .locationIds(List.of(UUID.randomUUID()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        assertThrows(AccessDeniedException.class, () -> SecurityUtils.requireLocationAccess(locationId));
    }

    @Test
    void shouldAllowArtistWhenLocationIsAssigned() {
        UUID locationId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .role(UserRole.ARTIST)
                .locationIds(List.of(locationId))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        SecurityUtils.requireLocationAccess(locationId);
    }

    @Test
    void shouldAllowAdminForAnyLocation() {
        UUID locationId = UUID.randomUUID();
        authenticate(UUID.randomUUID(), UUID.randomUUID(), UserRole.ADMIN);

        SecurityUtils.requireLocationAccess(locationId);
    }

    private void authenticate(UUID userId, UUID tenantId, UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(role)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
