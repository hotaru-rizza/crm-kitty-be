package com.inkflow.crm.module.email.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.module.email.dto.CreateEmailTemplateRequest;
import com.inkflow.crm.module.email.dto.EmailTemplatePreviewRequest;
import com.inkflow.crm.module.email.dto.EmailTemplateResponseDto;
import com.inkflow.crm.module.email.dto.UpdateEmailTemplateRequest;
import com.inkflow.crm.module.email.service.EmailTemplateService;
import com.inkflow.crm.security.RequirePermission;
import com.inkflow.crm.security.SecurityUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/emails/templates")
@RequiredArgsConstructor
@Tag(name = "CRM · Email Templates")
public class EmailTemplateController {

    private final EmailTemplateService emailTemplateService;

    @GetMapping
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<ApiResponse<List<EmailTemplateResponseDto>>> list() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ApiResponses.ok(emailTemplateService.list(tenantId));
    }

    @PostMapping
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<ApiResponse<EmailTemplateResponseDto>> create(
            @Valid @RequestBody CreateEmailTemplateRequest request) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId();
        EmailTemplateResponseDto created = emailTemplateService.create(tenantId, request, userId);
        log.info("Email template created via API: tenantId={} id={}", tenantId, created.id());
        return ApiResponses.ok(created);
    }

    @PutMapping("/{id}")
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<ApiResponse<EmailTemplateResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmailTemplateRequest request) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID userId = SecurityUtils.getCurrentUserId();
        EmailTemplateResponseDto updated = emailTemplateService.update(tenantId, id, request, userId);
        log.info("Email template updated via API: tenantId={} id={}", tenantId, id);
        return ApiResponses.ok(updated);
    }

    @DeleteMapping("/{id}")
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        emailTemplateService.delete(tenantId, id);
        log.info("Email template deleted via API: tenantId={} id={}", tenantId, id);
        return ApiResponses.empty();
    }

    @PostMapping("/preview")
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<String> previewDraft(@Valid @RequestBody EmailTemplatePreviewRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        String html = emailTemplateService.previewDraft(tenantId, request);
        log.info("Email template draft preview via API: tenantId={} triggerType={}", tenantId, request.triggerType());

        return ResponseEntity.ok(html);
    }

    @GetMapping("/{id}/preview")
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<String> preview(@PathVariable UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(emailTemplateService.preview(tenantId, id));
    }
}
