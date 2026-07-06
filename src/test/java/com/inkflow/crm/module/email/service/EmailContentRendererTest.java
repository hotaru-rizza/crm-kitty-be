package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.module.email.dto.RenderedEmail;
import com.inkflow.crm.module.email.enums.TemplateKey;
import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.template.RenderedContent;
import com.inkflow.crm.module.email.template.TemplateResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailContentRendererTest {

    @Mock private TemplateResolver templateResolver;
    @Mock private InkflowProperties inkflowProperties;

    @InjectMocks
    private EmailContentRenderer contentRenderer;

    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(inkflowProperties.getAppName()).thenReturn("TestApp");
    }

    @Test
    void render_substitutesVariablesAndWrapsLayout() {
        when(templateResolver.resolve(TENANT, TemplateKey.WELCOME_ONBOARD))
                .thenReturn(new RenderedContent("Welcome to {app_name}", "Hi {user_name} from {app_name}"));

        RenderedEmail email = contentRenderer.render(
                TENANT,
                TemplateKey.WELCOME_ONBOARD,
                Map.of("user_name", "Anna"),
                "Ink Studio",
                null
        );

        assertThat(email.subject()).isEqualTo("Welcome to TestApp");
        assertThat(email.html()).contains("TestApp");
        assertThat(email.html()).contains("Hi Anna from TestApp");
    }

    @Test
    void sampleVariables_includesAppName() {
        Map<String, String> variables = contentRenderer.sampleVariables(TemplateKey.BOOKING_CONFIRMED);

        assertThat(variables.get(TemplateVar.APP_NAME.getPlaceholder())).isEqualTo("TestApp");
        assertThat(variables.get(TemplateVar.CLIENT_NAME.getPlaceholder())).isEqualTo("Олена");
    }
}
