package com.inkflow.crm.module.email.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.module.email.dto.EmailComposeRequest;
import com.inkflow.crm.module.email.dto.EmailMessageDto;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.service.sending.BulkEmailService;
import com.inkflow.crm.module.email.service.sending.EmailMessageQueryService;
import com.inkflow.crm.security.RequirePermission;
import com.inkflow.crm.security.SecurityUtils;
import com.inkflow.crm.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
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

    private final EmailMessageQueryService emailMessageQueryService;
    private final BulkEmailService bulkEmailService;

    @GetMapping("/messages")
    @RequirePermission(Permission.EMAILS_VIEW)
    public ResponseEntity<ApiResponse<List<EmailMessageDto>>> getMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TriggerType triggerType,
            @RequestParam(required = false) EmailMessageStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String search) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();

        return ApiResponses.page(emailMessageQueryService.getMessages(
                tenantId, triggerType, status, from, to, search, PageRequest.of(page, size)));
    }

    @PostMapping("/send")
    @RequirePermission(Permission.EMAILS_SEND)
    public ResponseEntity<ApiResponse<SendEmailResultDto>> send(@Valid @RequestBody SendEmailRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        SendEmailResultDto result = bulkEmailService.sendBulk(tenantId, request);
        log.info("Bulk email queued via API: tenantId={} sent={} skipped={}", tenantId, result.sent(), result.skipped());

        return ApiResponses.ok(result);
    }

    @PostMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    @RequirePermission(Permission.EMAILS_VIEW)
    public ResponseEntity<String> preview(@Valid @RequestBody EmailComposeRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return ResponseEntity.ok(bulkEmailService.renderPreview(tenantId, request));
    }

    @PostMapping("/send-test")
    @RequirePermission(Permission.EMAILS_SEND)
    public ResponseEntity<ApiResponse<Void>> sendTest(@Valid @RequestBody EmailComposeRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UserPrincipal user = SecurityUtils.getCurrentUserOrThrow();
        String recipientName = user.getEmail().contains("@")
                ? user.getEmail().substring(0, user.getEmail().indexOf('@'))
                : user.getEmail();
        bulkEmailService.sendTest(tenantId, user.getEmail(), recipientName, request);
        return ApiResponses.empty();
    }
}
