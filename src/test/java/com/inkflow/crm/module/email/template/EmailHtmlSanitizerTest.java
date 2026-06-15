package com.inkflow.crm.module.email.template;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailHtmlSanitizerTest {

    @Test
    void sanitize_keepsAllowedHtmlAndVariables() {
        String input = "<p>Hi <span data-var=\"client_name\" class=\"email-var-chip\">{client_name}</span></p>";
        String result = EmailHtmlSanitizer.sanitize(input);

        assertThat(result).contains("client_name");
        assertThat(result).contains("<p>");
    }
}
