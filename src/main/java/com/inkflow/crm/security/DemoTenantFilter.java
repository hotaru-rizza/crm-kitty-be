package com.inkflow.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Blocks all write operations for the demo tenant.
 * GET requests pass through — the user can browse everything but cannot modify data.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoTenantFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Value("${demo.tenant-id:}")
    private String demoTenantIdStr;

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        if (demoTenantIdStr == null || demoTenantIdStr.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        if (!WRITE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            UUID tenantId = SecurityUtils.getCurrentTenantId();
            UUID demoTenantId = UUID.fromString(demoTenantIdStr);

            if (tenantId != null && tenantId.equals(demoTenantId)) {
                log.debug("Demo tenant {} blocked: {} {}", tenantId, request.getMethod(), request.getRequestURI());
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> body = Map.of(
                        "error", "DEMO_READONLY",
                        "message", "Це демо-версія. Створіть акаунт, щоб отримати повний доступ.",
                        "code", 403
                );
                response.getWriter().write(objectMapper.writeValueAsString(body));
                return;
            }
        } catch (Exception e) {
            log.warn("Could not check demo tenant: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
