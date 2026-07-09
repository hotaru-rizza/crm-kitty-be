package com.inkflow.crm.security;

import java.util.Optional;
import java.util.UUID;

/**
 * Single entry point for location-scoped reads inside a tenant.
 * Empty result = all locations (no filter).
 *
 * <p>Workspace scope ({@link #resolveFilter}) — lists, calendar, finance, analytics.
 * Entity scope ({@code X-Entity-Scope: true}) — detail pages; ignores workspace header.
 */
public final class LocationScope {

    public static final String ENTITY_SCOPE_HEADER = "X-Entity-Scope";

    private LocationScope() {
    }

    public static Optional<UUID> resolveFilter(UUID queryLocationId) {
        if (TenantContext.isEntityScope()) {
            return entityFilter(queryLocationId);
        }
        return workspaceFilter(queryLocationId);
    }

    static Optional<UUID> workspaceFilter(UUID queryLocationId) {
        UUID fromHeader = TenantContext.getCurrentLocation();
        if (fromHeader != null) {
            return Optional.of(fromHeader);
        }
        if (queryLocationId != null) {
            SecurityUtils.requireLocationAccess(queryLocationId);
            return Optional.of(queryLocationId);
        }
        return Optional.empty();
    }

    static Optional<UUID> entityFilter(UUID queryLocationId) {
        if (queryLocationId != null) {
            SecurityUtils.requireLocationAccess(queryLocationId);
            return Optional.of(queryLocationId);
        }
        return Optional.empty();
    }
}
