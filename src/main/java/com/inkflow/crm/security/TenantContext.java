package com.inkflow.crm.security;

import com.inkflow.crm.domain.enums.UserRole;

import java.util.List;
import java.util.UUID;

public class TenantContext {

    private static final ThreadLocal<UUID> currentTenant = new ThreadLocal<>();
    private static final ThreadLocal<UUID> currentUser = new ThreadLocal<>();
    private static final ThreadLocal<UserRole> currentRole = new ThreadLocal<>();
    private static final ThreadLocal<List<UUID>> currentLocationIds = new ThreadLocal<>();
    private static final ThreadLocal<UUID> currentLocation = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> entityScope = new ThreadLocal<>();

    public static UUID getCurrentTenant() {
        return currentTenant.get();
    }

    public static void setCurrentTenant(UUID tenantId) {
        currentTenant.set(tenantId);
    }

    public static UUID getCurrentUser() {
        return currentUser.get();
    }

    public static void setCurrentUser(UUID userId) {
        currentUser.set(userId);
    }

    public static UserRole getCurrentRole() {
        return currentRole.get();
    }

    public static void setCurrentRole(UserRole role) {
        currentRole.set(role);
    }

    public static List<UUID> getCurrentLocationIds() {
        return currentLocationIds.get();
    }

    public static void setCurrentLocationIds(List<UUID> locationIds) {
        currentLocationIds.set(locationIds);
    }

    public static UUID getCurrentLocation() {
        return currentLocation.get();
    }

    public static void setCurrentLocation(UUID locationId) {
        currentLocation.set(locationId);
    }

    public static boolean isEntityScope() {
        return Boolean.TRUE.equals(entityScope.get());
    }

    public static void setEntityScope(boolean value) {
        entityScope.set(value);
    }

    public static void clear() {
        currentTenant.remove();
        currentUser.remove();
        currentRole.remove();
        currentLocationIds.remove();
        currentLocation.remove();
        entityScope.remove();
    }
}
