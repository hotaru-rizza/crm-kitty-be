package com.inkflow.crm.security;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocationScopeTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveFilter_prefersHeaderOverQueryParam() {
        UUID headerLocation = UUID.randomUUID();
        UUID queryLocation = UUID.randomUUID();
        TenantContext.setCurrentLocation(headerLocation);

        Optional<UUID> result = LocationScope.resolveFilter(queryLocation);

        assertEquals(headerLocation, result.orElseThrow());
    }

    @Test
    void resolveFilter_usesQueryParamWhenHeaderMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateOwner(tenantId, List.of(locationId));

        Optional<UUID> result = LocationScope.resolveFilter(locationId);

        assertEquals(locationId, result.orElseThrow());
    }

    @Test
    void resolveFilter_emptyWhenNoHeaderOrQueryParam() {
        assertTrue(LocationScope.resolveFilter(null).isEmpty());
    }

    @Test
    void resolveFilter_rejectsInaccessibleQueryParam() {
        UUID tenantId = UUID.randomUUID();
        UUID allowed = UUID.randomUUID();
        UUID denied = UUID.randomUUID();
        authenticateArtist(tenantId, List.of(allowed));

        assertThrows(AccessDeniedException.class, () -> LocationScope.resolveFilter(denied));
    }

    @Test
    void resolveFilter_ignoresWorkspaceHeaderInEntityScope() {
        UUID headerLocation = UUID.randomUUID();
        UUID queryLocation = UUID.randomUUID();
        TenantContext.setCurrentLocation(headerLocation);
        TenantContext.setEntityScope(true);
        authenticateOwner(UUID.randomUUID(), List.of(queryLocation));

        Optional<UUID> result = LocationScope.resolveFilter(null);

        assertTrue(result.isEmpty());
    }

    @Test
    void resolveFilter_usesExplicitQueryParamInEntityScope() {
        UUID headerLocation = UUID.randomUUID();
        UUID queryLocation = UUID.randomUUID();
        TenantContext.setCurrentLocation(headerLocation);
        TenantContext.setEntityScope(true);
        authenticateOwner(UUID.randomUUID(), List.of(queryLocation));

        Optional<UUID> result = LocationScope.resolveFilter(queryLocation);

        assertEquals(queryLocation, result.orElseThrow());
    }

    private static void authenticateOwner(UUID tenantId, List<UUID> locationIds) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.OWNER)
                .locationIds(locationIds)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private static void authenticateArtist(UUID tenantId, List<UUID> locationIds) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(UserRole.ARTIST)
                .locationIds(locationIds)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
