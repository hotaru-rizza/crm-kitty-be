package com.inkflow.crm.config;

import com.inkflow.crm.module.consumer.security.ConsumerAuthFilter;
import com.inkflow.crm.security.DemoTenantFilter;
import com.inkflow.crm.security.JwtAuthenticationFilter;
import com.inkflow.crm.security.SubscriptionFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Profile("!dev")
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ConsumerAuthFilter consumerAuthFilter;
    private final SubscriptionFilter subscriptionFilter;
    private final DemoTenantFilter demoTenantFilter;

    @Bean
    public FilterRegistrationBean<ConsumerAuthFilter> disableConsumerFilterAutoRegistration() {
        var reg = new FilterRegistrationBean<>(consumerAuthFilter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterAutoRegistration() {
        var reg = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        reg.setEnabled(false);
        return reg;
    }


    @Bean
    @Order(1)
    public SecurityFilterChain consumerFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/public/consumer/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(consumerAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Chain 2: CRM panel + all other public routes.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain crmFilterChain(HttpSecurity http) throws Exception {
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
                                "/requests",
                                "/onboarding",
                                "/public/**",
                                "/payments/monobank/webhook"
                        ).permitAll()
                        .requestMatchers("/files/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(subscriptionFilter, JwtAuthenticationFilter.class)
                .addFilterAfter(demoTenantFilter, SubscriptionFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization", "X-Location-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
