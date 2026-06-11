package com.inkflow.crm.config;

import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import com.inkflow.crm.security.JwtAuthenticationFilter;
import com.inkflow.crm.module.consumer.security.ConsumerAuthFilter;
import com.inkflow.crm.security.DemoTenantFilter;
import com.inkflow.crm.security.SubscriptionFilter;
import com.inkflow.crm.security.TenantContext;
import com.inkflow.crm.support.TestSecurityHeaders;
import com.inkflow.crm.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("test")
@RequiredArgsConstructor
public class TestSecurityConfig {

    private final SubscriptionFilter subscriptionFilter;
    private final DemoTenantFilter demoTenantFilter;
    private final InkflowProperties inkflowProperties;

    @Bean
    public FilterRegistrationBean<ConsumerAuthFilter> disableConsumerFilterAutoRegistration(
            ConsumerAuthFilter consumerAuthFilter) {
        var reg = new FilterRegistrationBean<>(consumerAuthFilter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterAutoRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        var reg = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain consumerFilterChain(HttpSecurity http, TestConsumerAuthFilter consumerAuthFilter)
            throws Exception {
        http
                .securityMatcher("/public/consumer/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(consumerAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain crmFilterChain(HttpSecurity http, TestCrmAuthFilter crmAuthFilter) throws Exception {
        http
                .securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/**",
                                "/staff/accept-invite",
                                "/staff/invite/info/**",
                                "/onboarding",
                                "/public/**",
                                "/payments/monobank/webhook"
                        ).permitAll()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(crmAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(subscriptionFilter, TestCrmAuthFilter.class)
                .addFilterAfter(demoTenantFilter, SubscriptionFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(inkflowProperties.getCors().getAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Location-Id"));
        configuration.setAllowCredentials(inkflowProperties.getCors().isAllowCredentials());
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Component
    @Profile("test")
    static class TestCrmAuthFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String userIdHeader = request.getHeader(TestSecurityHeaders.CRM_USER_ID);
            String tenantIdHeader = request.getHeader(TestSecurityHeaders.CRM_TENANT_ID);

            if (StringUtils.hasText(userIdHeader) && StringUtils.hasText(tenantIdHeader)) {
                UUID userId = UUID.fromString(userIdHeader);
                UUID tenantId = UUID.fromString(tenantIdHeader);
                UserRole role = parseRole(request.getHeader(TestSecurityHeaders.CRM_ROLE));

                UserPrincipal principal = UserPrincipal.builder()
                        .id(userId)
                        .tenantId(tenantId)
                        .role(role)
                        .email("test@example.com")
                        .locationIds(Collections.emptyList())
                        .build();

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                TenantContext.setCurrentTenant(tenantId);
                TenantContext.setCurrentUser(userId);
                TenantContext.setCurrentRole(role);
            }

            try {
                filterChain.doFilter(request, response);
            } finally {
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }
        }

        private UserRole parseRole(String roleHeader) {
            if (!StringUtils.hasText(roleHeader)) {
                return UserRole.OWNER;
            }
            return UserRole.valueOf(roleHeader);
        }
    }

    @Component
    @Profile("test")
    @RequiredArgsConstructor
    static class TestConsumerAuthFilter extends OncePerRequestFilter {

        private final ConsumerUserRepository consumerUserRepository;

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String consumerIdHeader = request.getHeader(TestSecurityHeaders.CONSUMER_ID);

            if (StringUtils.hasText(consumerIdHeader)) {
                UUID consumerId = UUID.fromString(consumerIdHeader);
                ConsumerUser consumer = consumerUserRepository.findById(consumerId)
                        .orElseGet(() -> consumerUserRepository.save(
                                new ConsumerUser(consumerId, "consumer@test.com", null)));

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        consumer,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CONSUMER"))
                );
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }

            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
