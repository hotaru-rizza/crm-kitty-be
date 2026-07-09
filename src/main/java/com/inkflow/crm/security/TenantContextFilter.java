package com.inkflow.crm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
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
                 PreparedStatement ps = conn.prepareStatement("SELECT set_config('app.current_tenant', ?, false)")) {
                ps.setString(1, tenantId.toString());
                ps.execute();
            } catch (Exception e) {
                log.warn("Failed to set tenant context in DB session: {}", e.getMessage());
            }

        }

        filterChain.doFilter(request, response);
    }
}
