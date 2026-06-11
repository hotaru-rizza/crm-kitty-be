package com.inkflow.crm.module.email.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.module.email.dto.EmailLogDto;
import com.inkflow.crm.module.email.dto.EmailSettingsDto;
import com.inkflow.crm.module.email.dto.EmailStatsDto;
import com.inkflow.crm.module.email.dto.EmailTemplateDto;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.service.EmailManagementService;
import com.inkflow.crm.module.email.service.EmailService;
import com.inkflow.crm.security.RequirePermission;
import com.inkflow.crm.security.SecurityUtils;
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
public class EmailController {

    private final EmailService emailService;
    private final EmailManagementService emailManagementService;

    @GetMapping("/log")
    @RequirePermission("emails.view")
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
    @RequirePermission("emails.view")
    public ResponseEntity<ApiResponse<EmailStatsDto>> getStats() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ApiResponses.ok(emailService.getStats(tenantId));
    }

    @PostMapping("/send")
    @RequirePermission("emails.send")
    public ResponseEntity<ApiResponse<SendEmailResultDto>> send(@Valid @RequestBody SendEmailRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        SendEmailResultDto result = emailManagementService.sendBulk(tenantId, request);
        log.info("Bulk email sent via API: tenantId={} sent={} skipped={}", tenantId, result.sent(), result.skipped());

        return ApiResponses.ok(result);
    }

    @GetMapping("/templates")
    @RequirePermission("emails.manage")
    public ResponseEntity<ApiResponse<List<EmailTemplateDto>>> getTemplates() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ApiResponses.ok(emailManagementService.getTemplates(tenantId));
    }

    @PutMapping("/templates/{type}")
    @RequirePermission("emails.manage")
    public ResponseEntity<ApiResponse<EmailTemplateDto>> updateTemplate(
            @PathVariable String type,
            @RequestBody EmailTemplateDto dto) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        EmailTemplateDto updated = emailManagementService.updateTemplate(tenantId, type, dto);
        log.info("Email template updated via API: tenantId={} type={}", tenantId, type);

        return ApiResponses.ok(updated);
    }

    @DeleteMapping("/templates/{type}")
    @RequirePermission("emails.manage")
    public ResponseEntity<ApiResponse<Void>> resetTemplate(@PathVariable String type) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        emailManagementService.resetTemplate(tenantId, type);
        log.info("Email template reset via API: tenantId={} type={}", tenantId, type);

        return ApiResponses.empty();
    }

    @GetMapping("/settings")
    @RequirePermission("emails.manage")
    public ResponseEntity<ApiResponse<EmailSettingsDto>> getEmailSettings() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ApiResponses.ok(emailManagementService.getEmailSettings(tenantId));
    }

    @PatchMapping("/settings")
    @RequirePermission("emails.manage")
    public ResponseEntity<ApiResponse<EmailSettingsDto>> updateEmailSettings(@RequestBody EmailSettingsDto dto) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        EmailSettingsDto updated = emailManagementService.updateEmailSettings(tenantId, dto);
        log.info("Email settings updated via API: tenantId={}", tenantId);

        return ApiResponses.ok(updated);
    }
}
