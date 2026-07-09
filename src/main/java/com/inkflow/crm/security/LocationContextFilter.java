package com.inkflow.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ErrorResponse;
import com.inkflow.crm.common.exception.AccessDeniedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class LocationContextFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (TenantContext.getCurrentTenant() != null) {
                applyLocationHeader(request);
            }
            filterChain.doFilter(request, response);
        } catch (AccessDeniedException ex) {
            writeForbidden(response, ex);
        }
    }

    private void applyLocationHeader(HttpServletRequest request) {
        if ("true".equalsIgnoreCase(request.getHeader(LocationScope.ENTITY_SCOPE_HEADER))) {
            TenantContext.setEntityScope(true);
            return;
        }

        String locationHeader = request.getHeader("X-Location-Id");
        if (!StringUtils.hasText(locationHeader)) {
            return;
        }

        try {
            UUID locationId = UUID.fromString(locationHeader);
            UserPrincipal user = SecurityUtils.getCurrentUser();
            if (user != null && !user.hasAccessToLocation(locationId)) {
                throw AccessDeniedException.locationAccessDenied();
            }
            TenantContext.setCurrentLocation(locationId);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid location ID in header: {}", locationHeader);
        }
    }

    private void writeForbidden(HttpServletResponse response, AccessDeniedException ex) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.error(ErrorResponse.of(ex.getErrorCode().getCode(), ex.getMessage()))
        );
    }
}
