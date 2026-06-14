package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.enums.TemplateKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateResolverTest {

    private final TemplateResolver resolver = new TemplateResolver();
    private static final UUID TENANT = UUID.randomUUID();

    @Test
    void resolve_returnsCodeDefault() {
        RenderedContent result = resolver.resolve(TENANT, TemplateKey.BOOKING_CONFIRMED);

        RenderedContent expected = TemplateDefaults.get(TemplateKey.BOOKING_CONFIRMED);
        assertThat(result.subject()).isEqualTo(expected.subject());
        assertThat(result.body()).isEqualTo(expected.body());
    }
}
