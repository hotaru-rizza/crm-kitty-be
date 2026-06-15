package com.inkflow.crm.module.email.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.ApiResponses;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.module.email.service.EmailTemplateService;
import com.inkflow.crm.security.RequirePermission;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/emails/trigger-types")
@RequiredArgsConstructor
@Tag(name = "CRM · Email Trigger Types")
public class EmailTriggerTypeController {

    private final EmailTemplateService emailTemplateService;

    @GetMapping
    @RequirePermission(Permission.EMAILS_MANAGE)
    public ResponseEntity<ApiResponse<List<EmailTemplateService.TriggerTypeInfo>>> list() {
        return ApiResponses.ok(emailTemplateService.listTriggerTypes());
    }
}
