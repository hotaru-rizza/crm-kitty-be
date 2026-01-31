package com.inkflow.crm.security;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }

    public static UserPrincipal getCurrentUserOrThrow() {
        UserPrincipal user = getCurrentUser();
        if (user == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "User not authenticated");
        }
        return user;
    }

    public static UUID getCurrentTenantId() {
        UserPrincipal user = getCurrentUserOrThrow();
        return user.getTenantId();
    }

    public static UUID getCurrentUserId() {
        UserPrincipal user = getCurrentUserOrThrow();
        return user.getId();
    }

    public static void requireOwner() {
        UserPrincipal user = getCurrentUserOrThrow();
        if (!user.isOwner()) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }

    public static void requireAdminAccess() {
        UserPrincipal user = getCurrentUserOrThrow();
        if (!user.isAdmin()) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }

    public static void requireLocationAccess(UUID locationId) {
        UserPrincipal user = getCurrentUserOrThrow();
        if (!user.hasAccessToLocation(locationId)) {
            throw AccessDeniedException.locationAccessDenied();
        }
    }
}
