package com.inkflow.crm.config;

import com.inkflow.crm.security.SecurityUtils;
import com.inkflow.crm.security.UserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;
import java.util.UUID;

@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider() {
        return () -> {
            UserPrincipal user = SecurityUtils.getCurrentUser();
            return Optional.ofNullable(user).map(UserPrincipal::getId);
        };
    }
}
