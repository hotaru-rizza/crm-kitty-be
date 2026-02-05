package com.inkflow.crm.module.waiver.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.waiver.dto.*;
import com.inkflow.crm.module.waiver.service.WaiverService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/waivers")
@RequiredArgsConstructor
public class WaiverController {

    private final WaiverService waiverService;

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

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<ApiResponse<SignedWaiverDto>> getSignedWaiver(@PathVariable UUID appointmentId) {
        SignedWaiverDto signedWaiver = waiverService.getSignedWaiver(appointmentId);
        return ResponseEntity.ok(ApiResponse.success(signedWaiver));
    }

    @PostMapping("/sign")
    public ResponseEntity<ApiResponse<SignedWaiverDto>> signWaiver(
            @Valid @RequestBody SignWaiverRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        SignedWaiverDto signedWaiver = waiverService.signWaiver(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success(signedWaiver));
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
