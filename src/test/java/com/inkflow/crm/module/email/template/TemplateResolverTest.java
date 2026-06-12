package com.inkflow.crm.module.email.template;

import com.inkflow.crm.domain.entity.EmailTemplateOverride;
import com.inkflow.crm.domain.repository.EmailTemplateOverrideRepository;
import com.inkflow.crm.module.email.enums.TemplateKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateResolverTest {

    @Mock
    private EmailTemplateOverrideRepository overrideRepository;

    @InjectMocks
    private TemplateResolver resolver;

    private static final UUID TENANT = UUID.randomUUID();

    @Test
    void resolve_usesOverrideWhenPresent() {
        EmailTemplateOverride override = EmailTemplateOverride.builder()
                .tenantId(TENANT)
                .templateKey(TemplateKey.BOOKING_CONFIRMED.name())
                .locale("uk")
                .subject("Custom subject")
                .body("Custom body")
                .build();

        when(overrideRepository.findByTenantIdAndTemplateKeyAndLocale(TENANT, "BOOKING_CONFIRMED", "uk"))
                .thenReturn(Optional.of(override));

        RenderedContent result = resolver.resolve(TENANT, TemplateKey.BOOKING_CONFIRMED, "uk");

        assertThat(result.subject()).isEqualTo("Custom subject");
        assertThat(result.body()).isEqualTo("Custom body");
    }

    @Test
    void resolve_fallsBackToDefaultLocaleOverrideWhenRequestedLocaleHasNoOverride() {
        EmailTemplateOverride ukOverride = EmailTemplateOverride.builder()
                .tenantId(TENANT)
                .templateKey(TemplateKey.TEAM_INVITE.name())
                .locale("uk")
                .subject("UK subject")
                .body("UK body")
                .build();

        when(overrideRepository.findByTenantIdAndTemplateKeyAndLocale(TENANT, "TEAM_INVITE", "en"))
                .thenReturn(Optional.empty());
        when(overrideRepository.findByTenantIdAndTemplateKeyAndLocale(TENANT, "TEAM_INVITE", "uk"))
                .thenReturn(Optional.of(ukOverride));

        RenderedContent result = resolver.resolve(TENANT, TemplateKey.TEAM_INVITE, "en");

        assertThat(result.subject()).isEqualTo("UK subject");
    }

    @Test
    void resolve_fallsBackToCodeDefaultWhenNoOverrideExists() {
        when(overrideRepository.findByTenantIdAndTemplateKeyAndLocale(TENANT, "BOOKING_CONFIRMED", "uk"))
                .thenReturn(Optional.empty());

        RenderedContent result = resolver.resolve(TENANT, TemplateKey.BOOKING_CONFIRMED, "uk");

        RenderedContent expected = TemplateDefaults.get(TemplateKey.BOOKING_CONFIRMED, "uk");
        assertThat(result.subject()).isEqualTo(expected.subject());
        assertThat(result.body()).isEqualTo(expected.body());
    }

    @Test
    void resolve_usesDefaultLocaleWhenLocaleArgumentIsNull() {
        when(overrideRepository.findByTenantIdAndTemplateKeyAndLocale(TENANT, "BOOKING_CONFIRMED", "uk"))
                .thenReturn(Optional.empty());

        RenderedContent result = resolver.resolve(TENANT, TemplateKey.BOOKING_CONFIRMED, null);

        RenderedContent expected = TemplateDefaults.get(TemplateKey.BOOKING_CONFIRMED, "uk");
        assertThat(result.subject()).isEqualTo(expected.subject());
    }
}
