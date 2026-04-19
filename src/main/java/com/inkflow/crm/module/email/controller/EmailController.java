package com.inkflow.crm.module.email.controller;

import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import com.inkflow.crm.module.email.dto.EmailLogDto;
import com.inkflow.crm.module.email.dto.EmailSettingsDto;
import com.inkflow.crm.module.email.dto.EmailStatsDto;
import com.inkflow.crm.module.email.dto.EmailTemplateDto;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.service.EmailService;
import com.inkflow.crm.module.email.service.EmailTemplates;
import com.inkflow.crm.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/emails")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;
    private final ClientRepository clientRepository;
    private final CompanySettingsRepository companySettingsRepository;

    @GetMapping("/log")
    public ResponseEntity<Page<EmailLogDto>> getLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) EmailType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(emailService.getLog(tenantId, type, from, to, PageRequest.of(page, size)));
    }

    @GetMapping("/stats")
    public ResponseEntity<EmailStatsDto> getStats() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(emailService.getStats(tenantId));
    }

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@Valid @RequestBody SendEmailRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        List<Client> clients = clientRepository.findAllById(request.getClientIds());

        int sent = 0;
        int skipped = 0;

        for (Client client : clients) {
            if (!client.getTenantId().equals(tenantId)) continue;
            if (client.getEmail() == null || client.getEmail().isBlank()) {
                skipped++;
                continue;
            }
            emailService.sendManual(tenantId, client.getEmail(), client.getFullName(), request.getSubject(), request.getBody());
            sent++;
        }

        return ResponseEntity.ok(Map.of("sent", sent, "skipped", skipped));
    }

    @GetMapping("/templates")
    public ResponseEntity<List<EmailTemplateDto>> getTemplates() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId).orElse(null);
        Map<String, Map<String, String>> custom = settings != null ? settings.getEmailTemplates() : null;

        List<EmailTemplateDto> result = new ArrayList<>();
        for (String type : List.of("CONFIRMATION", "REMINDER", "AFTERCARE")) {
            Map<String, String> defaults = EmailTemplates.getDefaults(type);
            List<String> defaultFields = EmailTemplates.getDefaultFields(type);
            Map<String, String> saved = custom != null ? custom.get(type) : null;

            List<String> fields = defaultFields;
            if (saved != null && saved.containsKey("fields")) {
                String raw = saved.get("fields");
                fields = (raw == null || raw.isBlank()) ? List.of() : List.of(raw.split(","));
            }

            result.add(EmailTemplateDto.builder()
                    .type(type)
                    .subject(saved != null && saved.containsKey("subject") ? saved.get("subject") : defaults.get("subject"))
                    .body(saved != null && saved.containsKey("body") ? saved.get("body") : defaults.get("body"))
                    .fields(fields)
                    .build());
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/templates/{type}")
    public ResponseEntity<EmailTemplateDto> updateTemplate(
            @PathVariable String type,
            @RequestBody EmailTemplateDto dto
    ) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Settings not found"));

        Map<String, Map<String, String>> templates = settings.getEmailTemplates();
        if (templates == null) templates = new HashMap<>();

        Map<String, String> entry = new HashMap<>();
        entry.put("subject", dto.getSubject());
        entry.put("body", dto.getBody());
        entry.put("fields", dto.getFields() != null ? String.join(",", dto.getFields()) : "");
        templates.put(type.toUpperCase(), entry);
        settings.setEmailTemplates(templates);
        companySettingsRepository.save(settings);

        return ResponseEntity.ok(EmailTemplateDto.builder()
                .type(type.toUpperCase())
                .subject(dto.getSubject())
                .body(dto.getBody())
                .fields(dto.getFields())
                .build());
    }

    @DeleteMapping("/templates/{type}")
    public ResponseEntity<Void> resetTemplate(@PathVariable String type) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Settings not found"));

        Map<String, Map<String, String>> templates = settings.getEmailTemplates();
        if (templates != null) {
            templates.remove(type.toUpperCase());
            settings.setEmailTemplates(templates);
            companySettingsRepository.save(settings);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    public ResponseEntity<EmailSettingsDto> getEmailSettings() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId).orElse(null);
        if (settings == null) {
            return ResponseEntity.ok(EmailSettingsDto.builder()
                    .emailReminders(true)
                    .emailConfirmations(true)
                    .emailAftercare(false)
                    .reminderHoursBefore(24)
                    .build());
        }
        return ResponseEntity.ok(EmailSettingsDto.builder()
                .emailReminders(settings.getEmailReminders())
                .emailConfirmations(settings.getEmailConfirmations())
                .emailAftercare(settings.getEmailAftercare())
                .reminderHoursBefore(settings.getReminderHoursBefore())
                .build());
    }

    @PatchMapping("/settings")
    public ResponseEntity<EmailSettingsDto> updateEmailSettings(@RequestBody EmailSettingsDto dto) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        CompanySettings settings = companySettingsRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new RuntimeException("Settings not found"));

        if (dto.isEmailReminders() != settings.getEmailReminders()) settings.setEmailReminders(dto.isEmailReminders());
        if (dto.isEmailConfirmations() != settings.getEmailConfirmations()) settings.setEmailConfirmations(dto.isEmailConfirmations());
        if (dto.isEmailAftercare() != settings.getEmailAftercare()) settings.setEmailAftercare(dto.isEmailAftercare());
        if (dto.getReminderHoursBefore() > 0) settings.setReminderHoursBefore(dto.getReminderHoursBefore());

        settings = companySettingsRepository.save(settings);

        return ResponseEntity.ok(EmailSettingsDto.builder()
                .emailReminders(settings.getEmailReminders())
                .emailConfirmations(settings.getEmailConfirmations())
                .emailAftercare(settings.getEmailAftercare())
                .reminderHoursBefore(settings.getReminderHoursBefore())
                .build());
    }
}
