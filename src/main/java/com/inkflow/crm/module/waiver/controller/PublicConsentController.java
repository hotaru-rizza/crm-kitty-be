package com.inkflow.crm.module.waiver.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.waiver.dto.PublicConsentDto;
import com.inkflow.crm.module.waiver.dto.PublicSignRequest;
import com.inkflow.crm.module.waiver.dto.SignedWaiverDto;
import com.inkflow.crm.module.waiver.service.PublicConsentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/consent")
@RequiredArgsConstructor
public class PublicConsentController {

    private final PublicConsentService publicConsentService;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<PublicConsentDto>> getConsentForm(@PathVariable String token) {
        PublicConsentDto dto = publicConsentService.getConsentForm(token);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/{token}/sign")
    public ResponseEntity<ApiResponse<Void>> signConsent(
            @PathVariable String token,
            @Valid @RequestBody PublicSignRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        publicConsentService.signConsent(token, request, clientIp);
        return ResponseEntity.ok(ApiResponse.empty());
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
