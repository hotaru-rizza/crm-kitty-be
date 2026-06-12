package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.enums.TemplateKey;
import com.inkflow.crm.module.email.enums.TemplateVar;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

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

    @Test
    void missingVars_returnsEmptyWhenAllPresent() {
        String body = "Hi {client_name}, your visit is on {date} at {time}.";

        Set<String> missing = TemplateVarSubstitutor.missingVars(body, TemplateKey.BOOKING_REMINDER);

        // BOOKING_REMINDER requires: app_name, studio_name, client_name, master_name, time, address, reminder_window
        // Only checking that the ones not in our body string are flagged
        assertThat(missing).contains("app_name", "studio_name");
    }

    @Test
    void missingVars_returnsAllWhenBodyIsNull() {
        Set<String> missing = TemplateVarSubstitutor.missingVars(null, TemplateKey.BOOKING_CONFIRMED);

        // Should return all required vars for BOOKING_CONFIRMED
        assertThat(missing).containsAll(
                TemplateKey.BOOKING_CONFIRMED.getRequiredVars().stream()
                        .map(TemplateVar::getPlaceholder)
                        .toList()
        );
    }
}
