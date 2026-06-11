package com.inkflow.crm.module.email.mapper;

import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.module.email.dto.EmailTemplateDto;
import com.inkflow.crm.module.email.service.EmailTemplates;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EmailTemplateMapper {

    public static final List<String> MANAGED_TYPES = List.of("CONFIRMATION", "REMINDER", "AFTERCARE");

    public Map<String, String> getTemplateEntry(Map<String, Map<String, String>> templates, String type) {
        if (templates == null) {
            return null;
        }
        return templates.get(type);
    }

    public List<String> resolveActiveFields(Map<String, String> entry, String type) {
        return resolveFields(entry, EmailTemplates.getDefaultFields(type));
    }

    public String resolveSubject(Map<String, String> entry, String type, String studioName) {
        Map<String, String> defaults = EmailTemplates.getDefaults(type);
        String subjectTemplate = resolveField(entry, "subject", defaults.get("subject"));

        if (subjectTemplate == null || subjectTemplate.isBlank()) {
            subjectTemplate = fallbackSubject(type);
        }

        return subjectTemplate.replace("{{studio}}", studioName);
    }

    private String fallbackSubject(String type) {
        return switch (type.toUpperCase()) {
            case "CONFIRMATION" -> "Запис підтверджено — {{studio}}";
            case "REMINDER" -> "Нагадування про запис — {{studio}}";
            case "AFTERCARE" -> "Догляд після сеансу — {{studio}}";
            case "CANCELLATION" -> "Запис скасовано — {{studio}}";
            case "RESCHEDULE" -> "Час запису змінено — {{studio}}";
            default -> "{{studio}}";
        };
    }

    public EmailTemplateDto toDto(String type, Map<String, Map<String, String>> custom) {
        Map<String, String> defaults = EmailTemplates.getDefaults(type);
        List<String> defaultFields = EmailTemplates.getDefaultFields(type);
        Map<String, String> saved = custom != null ? custom.get(type) : null;

        return EmailTemplateDto.builder()
                .type(type)
                .subject(resolveField(saved, "subject", defaults.get("subject")))
                .body(resolveField(saved, "body", defaults.get("body")))
                .fields(resolveFields(saved, defaultFields))
                .build();
    }

    public Map<String, String> toStorageEntry(EmailTemplateDto dto) {
        Map<String, String> entry = new HashMap<>();
        entry.put("subject", dto.getSubject());
        entry.put("body", dto.getBody());
        entry.put("fields", dto.getFields() != null ? String.join(",", dto.getFields()) : "");
        return entry;
    }

    public Map<String, Map<String, String>> templatesOrEmpty(CompanySettings settings) {
        Map<String, Map<String, String>> templates = settings.getEmailTemplates();
        return templates != null ? templates : new HashMap<>();
    }

    private String resolveField(Map<String, String> saved, String key, String defaultValue) {
        if (saved != null && saved.containsKey(key)) {
            return saved.get(key);
        }
        return defaultValue;
    }

    private List<String> resolveFields(Map<String, String> saved, List<String> defaultFields) {
        if (saved == null || !saved.containsKey("fields")) {
            return defaultFields;
        }

        String raw = saved.get("fields");
        return (raw == null || raw.isBlank()) ? List.of() : List.of(raw.split(","));
    }
}
