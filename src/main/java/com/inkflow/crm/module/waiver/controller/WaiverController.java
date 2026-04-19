package com.inkflow.crm.module.waiver.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.dto.PaginationDto;
import com.inkflow.crm.module.waiver.dto.*;
import com.inkflow.crm.module.waiver.service.WaiverService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/waivers")
@RequiredArgsConstructor
public class WaiverController {

    private final WaiverService waiverService;

    // ──────────────── Template CRUD ────────────────

    @GetMapping("/templates")
    public ResponseEntity<ApiResponse<List<WaiverTemplateDto>>> listTemplates() {
        List<WaiverTemplateDto> templates = waiverService.listTemplates();
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<WaiverTemplateDto>> createTemplate(
            @Valid @RequestBody CreateTemplateRequest request) {
        WaiverTemplateDto template = waiverService.createTemplate(request);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<ApiResponse<WaiverTemplateDto>> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTemplateRequest request) {
        WaiverTemplateDto template = waiverService.updateTemplate(id, request);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTemplate(@PathVariable UUID id) {
        waiverService.deleteTemplate(id);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    @PutMapping("/templates/{id}/activate")
    public ResponseEntity<ApiResponse<WaiverTemplateDto>> activateTemplate(@PathVariable UUID id) {
        WaiverTemplateDto template = waiverService.activateTemplate(id);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PutMapping("/templates/{id}/deactivate")
    public ResponseEntity<ApiResponse<WaiverTemplateDto>> deactivateTemplate(@PathVariable UUID id) {
        WaiverTemplateDto template = waiverService.deactivateTemplate(id);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    // ──────────────── Active template (for signing flow) ────────────────

    @GetMapping("/template")
    public ResponseEntity<ApiResponse<WaiverTemplateDto>> getActiveTemplate() {
        WaiverTemplateDto template = waiverService.getActiveTemplate();
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @GetMapping("/template/{id}")
    public ResponseEntity<ApiResponse<WaiverTemplateDto>> getTemplateById(@PathVariable UUID id) {
        WaiverTemplateDto template = waiverService.getTemplateById(id);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    // ──────────────── Consent token ────────────────

    @PostMapping("/consent-token/{appointmentId}")
    public ResponseEntity<ApiResponse<String>> generateConsentToken(@PathVariable UUID appointmentId) {
        String token = waiverService.generateConsentToken(appointmentId);
        return ResponseEntity.ok(ApiResponse.success(token));
    }

    // ──────────────── Signing ────────────────

    @PostMapping("/sign")
    public ResponseEntity<ApiResponse<SignedWaiverDto>> signWaiver(
            @Valid @RequestBody SignWaiverRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        SignedWaiverDto signedWaiver = waiverService.signWaiver(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(signedWaiver));
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<ApiResponse<SignedWaiverDto>> getSignedWaiver(@PathVariable UUID appointmentId) {
        SignedWaiverDto signedWaiver = waiverService.getSignedWaiver(appointmentId);
        return ResponseEntity.ok(ApiResponse.success(signedWaiver));
    }

    // ──────────────── Signed documents list ────────────────

    @GetMapping("/signed")
    public ResponseEntity<ApiResponse<List<SignedWaiverDto>>> listSignedWaivers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SignedWaiverDto> result = waiverService.listSignedWaivers(page, size);
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), PaginationDto.from(result)));
    }

    // ──────────────── Util ────────────────

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
