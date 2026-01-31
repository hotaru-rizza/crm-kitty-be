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

/**
 * DEV MODE: Bypasses JWT auth and uses hardcoded user.
 * Active only with 'dev' profile.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("dev")
public class DevSecurityConfig {

    // ═══════════════════════════════════════════════════════════════════
    // HARDCODED DEV USER - reads tenant_id from DB on first request
    // ═══════════════════════════════════════════════════════════════════
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
        config.addAllowedOriginPattern("*");  // Allow ALL origins
        config.addAllowedMethod("*");          // Allow ALL methods
        config.addAllowedHeader("*");          // Allow ALL headers
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

        // Lazy-loaded on first request
        private volatile UUID tenantId;
        private volatile String email;
        private volatile UserRole role;
        private volatile List<UUID> locationIds;

        private synchronized void loadUserIfNeeded() {
            if (tenantId != null) return;
            
            try {
                // Simple native query - no lazy loading issues
                List<Tuple> results = entityManager.createNativeQuery(
                        "SELECT tenant_id, email, role FROM staff WHERE id = :id", Tuple.class)
                        .setParameter("id", DEV_USER_ID)
                        .getResultList();

                if (!results.isEmpty()) {
                    Tuple row = results.get(0);
                    tenantId = (UUID) row.get("tenant_id");
                    email = (String) row.get("email");
                    role = UserRole.valueOf((String) row.get("role"));

                    // Load locations
                    @SuppressWarnings("unchecked")
                    List<UUID> locs = entityManager.createNativeQuery(
                            "SELECT id FROM locations WHERE tenant_id = :tid AND deleted_at IS NULL", UUID.class)
                            .setParameter("tid", tenantId)
                            .getResultList();
                    locationIds = locs;

                    log.warn("════════════════════════════════════════════════════════");
                    log.warn("  DEV USER: {} ({}) | Tenant: {}", email, role, tenantId);
                    log.warn("════════════════════════════════════════════════════════");
                } else {
                    log.error("❌ DEV USER NOT FOUND: {}", DEV_USER_ID);
                    tenantId = DEV_USER_ID; // fallback to prevent NPE
                    email = "unknown";
                    role = UserRole.OWNER;
                    locationIds = Collections.emptyList();
                }
            } catch (Exception e) {
                log.error("❌ Failed to load dev user: {}", e.getMessage());
                tenantId = DEV_USER_ID;
                email = "error";
                role = UserRole.OWNER;
                locationIds = Collections.emptyList();
            }
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            
            loadUserIfNeeded();

            UserPrincipal devUser = UserPrincipal.builder()
                    .id(DEV_USER_ID)
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
            TenantContext.setCurrentUser(DEV_USER_ID);
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
