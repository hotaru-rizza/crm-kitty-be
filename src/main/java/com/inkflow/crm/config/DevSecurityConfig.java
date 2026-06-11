package com.inkflow.crm.config;

import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.security.TenantContext;
import com.inkflow.crm.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("dev")
public class DevSecurityConfig {


    public static final UUID DEV_USER_ID = UUID.fromString("aaaa1111-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Bean
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http, DevAuthFilter devAuthFilter) throws Exception {
        log.warn("⚠️  DEV MODE ACTIVE - Security disabled, user: {}", DEV_USER_ID);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(devAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Slf4j
    @Component
    @Profile("dev")
    @Order(1)
    public static class DevAuthFilter extends OncePerRequestFilter {

        @Autowired
        private EntityManager entityManager;

        @Autowired
        private com.inkflow.crm.security.JwtTokenProvider jwtTokenProvider;


        private volatile UUID tenantId;
        private volatile String email;
        private volatile UserRole role;
        private volatile List<UUID> locationIds;
        private volatile UUID resolvedUserId;

        private synchronized void loadUserIfNeeded() {
            if (tenantId != null) return;

            try {
                List<Tuple> results = entityManager.createNativeQuery(
                        "SELECT id, auth_user_id, tenant_id, email, role FROM staff " +
                        "WHERE role = 'OWNER' AND deleted_at IS NULL ORDER BY created_at LIMIT 1", Tuple.class)
                        .getResultList();

                if (!results.isEmpty()) {
                    Tuple row = results.get(0);
                    resolvedUserId = (UUID) row.get("id");
                    tenantId      = (UUID) row.get("tenant_id");
                    email         = (String) row.get("email");
                    role          = UserRole.valueOf((String) row.get("role"));

                    @SuppressWarnings("unchecked")
                    List<UUID> locs = entityManager.createNativeQuery(
                            "SELECT id FROM locations WHERE tenant_id = :tid AND deleted_at IS NULL", UUID.class)
                            .setParameter("tid", tenantId)
                            .getResultList();
                    locationIds = locs;

                    log.warn("════════════════════════════════════════════════════════");
                    log.warn("  DEV USER: {} ({}) | Tenant: {} | ID: {}", email, role, tenantId, resolvedUserId);
                    log.warn("════════════════════════════════════════════════════════");
                } else {
                    log.error("❌ No OWNER staff found in DB — run onboarding first");
                    resolvedUserId = DEV_USER_ID;
                    tenantId       = DEV_USER_ID;
                    email          = "unknown";
                    role           = UserRole.OWNER;
                    locationIds    = Collections.emptyList();
                }
            } catch (Exception e) {
                log.error("❌ Failed to load dev user: {}", e.getMessage());
                resolvedUserId = DEV_USER_ID;
                tenantId       = DEV_USER_ID;
                email          = "error";
                role           = UserRole.OWNER;
                locationIds    = Collections.emptyList();
            }
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {


            String bearerToken = request.getHeader("Authorization");
            if (org.springframework.util.StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
                String jwt = bearerToken.substring(7);
                try {
                    if (jwtTokenProvider.validateToken(jwt)) {
                        com.inkflow.crm.security.UserPrincipal jwtUser = jwtTokenProvider.getUserPrincipal(jwt);
                        if (jwtUser.getRole() != null && jwtUser.getTenantId() != null) {
                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(jwtUser, null, jwtUser.getAuthorities());
                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            TenantContext.setCurrentTenant(jwtUser.getTenantId());
                            TenantContext.setCurrentUser(jwtUser.getId());
                            TenantContext.setCurrentRole(jwtUser.getRole());
                            TenantContext.setCurrentLocationIds(jwtUser.getLocationIds());
                            try {
                                filterChain.doFilter(request, response);
                            } finally {
                                TenantContext.clear();
                                SecurityContextHolder.clearContext();
                            }
                            return;
                        }
                        log.debug("JWT valid but missing role/tenant claims, falling back to dev user");
                    }
                } catch (Exception e) {
                    log.debug("JWT validation failed in dev mode, falling back to dev user: {}", e.getMessage());
                }
            }


            loadUserIfNeeded();

            UserPrincipal devUser = UserPrincipal.builder()
                    .id(resolvedUserId)
                    .authUserId(resolvedUserId.toString())
                    .email(email)
                    .tenantId(tenantId)
                    .role(role)
                    .locationIds(locationIds)
                    .build();

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(devUser, null, devUser.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            TenantContext.setCurrentTenant(tenantId);
            TenantContext.setCurrentUser(resolvedUserId);
            TenantContext.setCurrentRole(role);
            TenantContext.setCurrentLocationIds(locationIds);

            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        }
    }
}
