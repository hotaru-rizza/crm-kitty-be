package com.inkflow.crm.security;

import com.inkflow.crm.common.exception.AccessDeniedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@Profile("!dev")
public class TenantContextFilter extends OncePerRequestFilter {

    private final DataSource dataSource;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        UUID tenantId = TenantContext.getCurrentTenant();

        if (tenantId != null) {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SET app.current_tenant = ?")) {
                ps.setString(1, tenantId.toString());
                ps.execute();
            } catch (Exception e) {
                log.warn("Failed to set tenant context in DB session: {}", e.getMessage());
            }

            String locationHeader = request.getHeader("X-Location-Id");
            if (StringUtils.hasText(locationHeader)) {
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
        }

        filterChain.doFilter(request, response);
    }
}
