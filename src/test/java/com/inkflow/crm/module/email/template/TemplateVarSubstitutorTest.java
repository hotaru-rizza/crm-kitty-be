package com.inkflow.crm.module.email.template;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateVarSubstitutorTest {

    @Test
    void substitute_replacesKnownPlaceholders() {
        String text = "Hello, {client_name}! Welcome to {studio_name}.";
        Map<String, String> vars = Map.of("client_name", "Anna", "studio_name", "Ink Studio");

        String result = TemplateVarSubstitutor.substitute(text, vars);

        assertThat(result).isEqualTo("Hello, Anna! Welcome to Ink Studio.");
    }

    @Test
    void substitute_leavesUnknownPlaceholdersAsIs() {
        String text = "Hi {user_name}, visit {unknown_var}.";
        Map<String, String> vars = Map.of("user_name", "Bob");

        String result = TemplateVarSubstitutor.substitute(text, vars);

        assertThat(result).isEqualTo("Hi Bob, visit {unknown_var}.");
    }

    @Test
    void substitute_handlesNullText() {
        assertThat(TemplateVarSubstitutor.substitute(null, Map.of("x", "y"))).isEqualTo("");
    }

    @Test
    void substitute_handlesNullVars() {
        String text = "Hello {client_name}";
        assertThat(TemplateVarSubstitutor.substitute(text, null)).isEqualTo("Hello {client_name}");
    }

    @Test
    void substitute_handlesEmptyVars() {
        String text = "No vars here.";
        assertThat(TemplateVarSubstitutor.substitute(text, Map.of())).isEqualTo("No vars here.");
    }

    @Test
    void substitute_replacesMultipleOccurrences() {
        String text = "{app_name} is great. Use {app_name} today!";
        Map<String, String> vars = Map.of("app_name", "CRM");

        String result = TemplateVarSubstitutor.substitute(text, vars);

        assertThat(result).isEqualTo("CRM is great. Use CRM today!");
    }

}
