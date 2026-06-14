package com.inkflow.crm.module.email.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.module.email.dto.EmailMessageDto;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.service.sending.BulkEmailService;
import com.inkflow.crm.module.email.service.sending.EmailMessageQueryService;
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

    private final EmailMessageQueryService emailMessageQueryService;
    private final BulkEmailService bulkEmailService;

    @GetMapping("/messages")
    @RequirePermission(Permission.EMAILS_VIEW)
    public ResponseEntity<ApiResponse<List<EmailMessageDto>>> getMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TriggerType triggerType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {

        UUID tenantId = SecurityUtils.getCurrentTenantId();

        return ApiResponses.page(emailMessageQueryService.getMessages(
                tenantId, triggerType, from, to, PageRequest.of(page, size)));
    }

    @PostMapping("/send")
    @RequirePermission(Permission.EMAILS_SEND)
    public ResponseEntity<ApiResponse<SendEmailResultDto>> send(@Valid @RequestBody SendEmailRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        SendEmailResultDto result = bulkEmailService.sendBulk(tenantId, request);
        log.info("Bulk email queued via API: tenantId={} sent={} skipped={}", tenantId, result.sent(), result.skipped());

        return ApiResponses.ok(result);
    }
}
