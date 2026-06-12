package com.inkflow.crm.module.email.controller;

import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.module.email.dto.*;
import com.inkflow.crm.module.email.service.EmailManagementService;
import com.inkflow.crm.module.email.service.EmailService;
import com.inkflow.crm.module.email.service.EmailTemplateService;
import com.inkflow.crm.security.RequirePermission;
import com.inkflow.crm.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/emails")
@RequiredArgsConstructor
@Tag(name = "CRM · Email")
public class EmailController {

    private final EmailService emailService;
    private final EmailManagementService emailManagementService;
    private final EmailTemplateService emailTemplateService;

    @GetMapping("/log")
    @RequirePermission(Permission.EMAILS_VIEW)
    public ResponseEntity<ApiResponse<List<EmailLogDto>>> getLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) EmailType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ApiResponses.page(emailService.getLog(tenantId, type, from, to, PageRequest.of(page, size)));
    }

    @GetMapping("/stats")
    @RequirePermission(Permission.EMAILS_VIEW)
    public ResponseEntity<ApiResponse<EmailStatsDto>> getStats() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ApiResponses.ok(emailService.getStats(tenantId));
    }

    @PostMapping("/send")
    @RequirePermission(Permission.EMAILS_SEND)
    public ResponseEntity<ApiResponse<SendEmailResultDto>> send(@Valid @RequestBody SendEmailRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        SendEmailResultDto result = emailManagementService.sendBulk(tenantId, request);
        log.info("Bulk email sent via API: tenantId={} sent={} skipped={}", tenantId, result.sent(), result.skipped());

        return ApiResponses.ok(result);
    }

    @GetMapping("/templates")
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<ApiResponse<List<TemplateListItemDto>>> getTemplates(
            @RequestParam(defaultValue = "uk") String locale) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ApiResponses.ok(emailTemplateService.listConfigurable(tenantId, locale));
    }

    @PutMapping("/templates/{key}")
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<ApiResponse<TemplateListItemDto>> updateTemplate(
            @PathVariable String key,
            @RequestParam(defaultValue = "uk") String locale,
            @Valid @RequestBody UpdateTemplateRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID updatedBy = SecurityUtils.getCurrentUserId();
        TemplateListItemDto updated = emailTemplateService.upsertOverride(tenantId, key, locale, request, updatedBy);
        log.info("Email template updated: tenantId={} key={} locale={}", tenantId, key, locale);

        return ApiResponses.ok(updated);
    }

    @DeleteMapping("/templates/{key}")
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<ApiResponse<Void>> resetTemplate(
            @PathVariable String key,
            @RequestParam(defaultValue = "uk") String locale) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        emailTemplateService.resetOverride(tenantId, key, locale);
        log.info("Email template reset: tenantId={} key={} locale={}", tenantId, key, locale);

        return ApiResponses.empty();
    }

    @GetMapping("/templates/{key}/preview")
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<String> previewTemplate(
            @PathVariable String key,
            @RequestParam(defaultValue = "uk") String locale) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(emailTemplateService.preview(tenantId, key, locale));
    }

    @GetMapping("/settings")
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<ApiResponse<EmailSettingsDto>> getEmailSettings() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ApiResponses.ok(emailManagementService.getEmailSettings(tenantId));
    }

    @PatchMapping("/settings")
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<ApiResponse<EmailSettingsDto>> updateEmailSettings(@RequestBody EmailSettingsDto dto) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        EmailSettingsDto updated = emailManagementService.updateEmailSettings(tenantId, dto);
        log.info("Email settings updated via API: tenantId={}", tenantId);

        return ApiResponses.ok(updated);
    }
}
