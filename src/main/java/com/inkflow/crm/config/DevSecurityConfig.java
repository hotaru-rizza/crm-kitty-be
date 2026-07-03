package com.inkflow.crm.config;

import com.inkflow.crm.module.consumer.security.ConsumerAuthFilter;
import com.inkflow.crm.security.TenantContext;
import com.inkflow.crm.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import java.util.UUID;


@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("dev")
@RequiredArgsConstructor
public class DevSecurityConfig {

    private final ConsumerAuthFilter consumerAuthFilter;

    public static final UUID DEV_USER_ID = UUID.fromString("aaaa1111-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Bean
    public FilterRegistrationBean<ConsumerAuthFilter> disableConsumerFilterAutoRegistration() {
        var reg = new FilterRegistrationBean<>(consumerAuthFilter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain devConsumerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(DevSecurityConfig::isConsumerApiPath)
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(consumerAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http, DevAuthFilter devAuthFilter) throws Exception {
        log.warn("⚠️  DEV MODE ACTIVE - Security disabled, user: {}", DEV_USER_ID);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(consumerAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(devAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    static boolean isConsumerApiPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && servletPath.startsWith("/public/consumer")) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/public/consumer/");
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
        private com.inkflow.crm.security.JwtTokenProvider jwtTokenProvider;

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return isConsumerApiPath(request);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {

            UserPrincipal principal = resolvePrincipal(request);
            if (principal != null) {
                authenticate(principal, request);
            }

            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        }

        /**
         * Dev mode authenticates strictly as the JWT's own user. There is no
         * "impersonate first OWNER" fallback: a missing/invalid token leaves the
         * request unauthenticated, so secured endpoints honestly return 401 instead
         * of silently logging in as someone else.
         */
        private UserPrincipal resolvePrincipal(HttpServletRequest request) {
            String bearerToken = request.getHeader("Authorization");
            if (!org.springframework.util.StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
                log.warn("DEV auth: no Bearer token on {} → request stays unauthenticated", request.getRequestURI());
                return null;
            }

            String jwt = bearerToken.substring(7);
            try {
                if (jwtTokenProvider.validateToken(jwt)) {
                    UserPrincipal jwtUser = jwtTokenProvider.getUserPrincipal(jwt);
                    log.info("DEV auth via JWT: email={} tenant={} authUserId={}",
                            jwtUser.getEmail(), jwtUser.getTenantId(), jwtUser.getAuthUserId());
                    return jwtUser;
                }
                log.warn("DEV auth: validateToken=false → request stays unauthenticated");
            } catch (Exception e) {
                log.warn("DEV auth: JWT processing threw ({}) → request stays unauthenticated", e.getMessage());
            }
            return null;
        }

        private void authenticate(UserPrincipal principal, HttpServletRequest request) {
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

            TenantContext.setCurrentTenant(principal.getTenantId());
            TenantContext.setCurrentUser(principal.getId());
            TenantContext.setCurrentRole(principal.getRole());
            TenantContext.setCurrentLocationIds(principal.getLocationIds());
        }
    }
}
