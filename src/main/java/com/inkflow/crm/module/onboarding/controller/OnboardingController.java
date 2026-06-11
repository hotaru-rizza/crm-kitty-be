package com.inkflow.crm.module.onboarding.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.module.onboarding.dto.OnboardingRequest;
import com.inkflow.crm.module.onboarding.dto.OnboardingResponse;
import com.inkflow.crm.module.onboarding.service.OnboardingService;
import com.inkflow.crm.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;

@Slf4j
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
@Tag(name = "System · Onboarding")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public ResponseEntity<ApiResponse<OnboardingResponse>> completeOnboarding(
            @Valid @RequestBody OnboardingRequest request,
            HttpServletRequest httpRequest) {

        DecodedJWT jwt = jwtTokenProvider.verifyToken(extractToken(httpRequest));

        UUID supabaseUserId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaim("email").asString();
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Email claim is required");
        }

        OnboardingResponse response = onboardingService.completeOnboarding(supabaseUserId, email, request);
        log.info("Onboarding completed via API: tenantId={} userId={}", response.getTenantId(), response.getUserId());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new ApiException(ErrorCode.UNAUTHORIZED, "Authorization token is required");
    }
}
