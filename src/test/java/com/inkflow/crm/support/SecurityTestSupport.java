package com.inkflow.crm.support;

import com.inkflow.crm.support.TestSecurityHeaders;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Collections;
import java.util.UUID;

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
    }

    public static void authenticate(Staff staff) {
        authenticate(staff.getId(), staff.getTenantId(), staff.getRole());
    }

    public static void clearAuthentication() {
        SecurityContextHolder.clearContext();
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

    public static RequestPostProcessor consumerUser(UUID consumerId) {
        return request -> {
            request.addHeader(TestSecurityHeaders.CONSUMER_ID, consumerId.toString());
            return request;
        };
    }
}
