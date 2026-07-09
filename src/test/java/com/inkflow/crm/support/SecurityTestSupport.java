package com.inkflow.crm.support;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.security.LocationScope;
import com.inkflow.crm.security.TenantContext;
import com.inkflow.crm.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SecurityTestSupport {

    private SecurityTestSupport() {
    }

    public static void authenticate(UUID userId, UUID tenantId, UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(role)
                .email("test@example.com")
                .locationIds(Collections.emptyList())
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        TenantContext.setCurrentTenant(tenantId);
        TenantContext.setCurrentUser(userId);
        TenantContext.setCurrentRole(role);
    }

    public static void authenticate(Staff staff) {
        authenticate(staff.getId(), staff.getTenantId(), staff.getRole());
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    public static RequestPostProcessor crmUser(UUID userId, UUID tenantId, UserRole role) {
        return request -> {
            request.addHeader(TestSecurityHeaders.CRM_USER_ID, userId.toString());
            request.addHeader(TestSecurityHeaders.CRM_TENANT_ID, tenantId.toString());
            request.addHeader(TestSecurityHeaders.CRM_ROLE, role.name());
            return request;
        };
    }

    public static RequestPostProcessor crmUser(Staff staff) {
        return crmUser(staff.getId(), staff.getTenantId(), staff.getRole());
    }

    public static RequestPostProcessor crmUserWithLocations(Staff staff, List<UUID> locationIds) {
        return crmUserWithLocations(staff.getId(), staff.getTenantId(), staff.getRole(), locationIds);
    }

    public static RequestPostProcessor crmUserWithLocations(UUID userId, UUID tenantId, UserRole role, List<UUID> locationIds) {
        return request -> {
            request.addHeader(TestSecurityHeaders.CRM_USER_ID, userId.toString());
            request.addHeader(TestSecurityHeaders.CRM_TENANT_ID, tenantId.toString());
            request.addHeader(TestSecurityHeaders.CRM_ROLE, role.name());
            if (locationIds != null && !locationIds.isEmpty()) {
                request.addHeader(
                        TestSecurityHeaders.CRM_LOCATION_IDS,
                        locationIds.stream().map(UUID::toString).collect(Collectors.joining(","))
                );
            }
            return request;
        };
    }

    public static RequestPostProcessor locationHeader(UUID locationId) {
        return request -> {
            request.addHeader("X-Location-Id", locationId.toString());
            return request;
        };
    }

    public static RequestPostProcessor entityScopeHeader() {
        return request -> {
            request.addHeader(LocationScope.ENTITY_SCOPE_HEADER, "true");
            return request;
        };
    }

    public static RequestPostProcessor consumerUser(UUID consumerId) {
        return request -> {
            request.addHeader(TestSecurityHeaders.CONSUMER_ID, consumerId.toString());
            return request;
        };
    }
}
