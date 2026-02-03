package com.inkflow.crm.module.onboarding.controller;

import com.inkflow.crm.common.dto.ApiResponse;
import com.inkflow.crm.module.onboarding.dto.OnboardingRequest;
import com.inkflow.crm.module.onboarding.dto.OnboardingResponse;
import com.inkflow.crm.module.onboarding.service.OnboardingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping
    public ResponseEntity<ApiResponse<OnboardingResponse>> completeOnboarding(
            @Valid @RequestBody OnboardingRequest request,
            HttpServletRequest httpRequest) {
        
        String token = extractToken(httpRequest);
        DecodedJWT jwt = JWT.decode(token);
        
        UUID supabaseUserId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaim("email").asString();
        
        OnboardingResponse response = onboardingService.completeOnboarding(supabaseUserId, email, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        throw new RuntimeException("No authorization token provided");
    }
}
