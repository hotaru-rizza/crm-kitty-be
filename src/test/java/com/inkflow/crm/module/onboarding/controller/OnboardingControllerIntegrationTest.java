package com.inkflow.crm.module.onboarding.controller;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.module.onboarding.dto.OnboardingRequest;
import com.inkflow.crm.module.onboarding.dto.OnboardingResponse;
import com.inkflow.crm.module.onboarding.service.OnboardingService;
import com.inkflow.crm.security.JwtTokenProvider;
import com.inkflow.crm.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
class OnboardingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private OnboardingService onboardingService;

    @Test
    void completeOnboarding_withInvalidToken_returnsUnauthorized() throws Exception {
        when(jwtTokenProvider.verifyToken("invalid-token"))
                .thenThrow(new ApiException(ErrorCode.INVALID_TOKEN, "Invalid or expired token"));

        mockMvc.perform(post("/onboarding")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.error.code").value("INVALID_TOKEN"));
    }

    @Test
    void completeOnboarding_withMalformedAuthorizationHeader_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/onboarding")
                        .header("Authorization", "NotBearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void completeOnboarding_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void completeOnboarding_withValidToken_returnsOk() throws Exception {
        UUID supabaseUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        DecodedJWT jwt = mockJwt(supabaseUserId, "owner@test.com");
        when(jwtTokenProvider.verifyToken("valid-token")).thenReturn(jwt);
        when(onboardingService.completeOnboarding(eq(supabaseUserId), eq("owner@test.com"), any(OnboardingRequest.class)))
                .thenReturn(OnboardingResponse.builder()
                        .userId(staffId)
                        .tenantId(tenantId)
                        .tenantName("Ink Studio")
                        .role("owner")
                        .success(true)
                        .build());

        mockMvc.perform(post("/onboarding")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.data.userId").value(staffId.toString()));

        verify(onboardingService).completeOnboarding(eq(supabaseUserId), eq("owner@test.com"), any(OnboardingRequest.class));
    }

    @Test
    void completeOnboarding_withoutEmailClaim_returnsBadRequest() throws Exception {
        UUID supabaseUserId = UUID.randomUUID();
        DecodedJWT jwt = mockJwt(supabaseUserId, null);
        when(jwtTokenProvider.verifyToken("no-email-token")).thenReturn(jwt);

        mockMvc.perform(post("/onboarding")
                        .header("Authorization", "Bearer no-email-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void completeOnboarding_withInvalidBody_returnsBadRequest() throws Exception {
        OnboardingRequest request = new OnboardingRequest();
        request.setFirstName("A");
        request.setLastName("B");
        request.setCompanyName("X");

        mockMvc.perform(post("/onboarding")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private OnboardingRequest validRequest() {
        OnboardingRequest request = new OnboardingRequest();
        request.setFirstName("Alex");
        request.setLastName("Artist");
        request.setCompanyName("Ink Studio");
        request.setTeamSize("solo");
        return request;
    }

    private DecodedJWT mockJwt(UUID supabaseUserId, String email) {
        DecodedJWT jwt = mock(DecodedJWT.class);
        Claim emailClaim = mock(Claim.class);

        when(jwt.getSubject()).thenReturn(supabaseUserId.toString());
        when(jwt.getClaim("email")).thenReturn(emailClaim);
        when(emailClaim.asString()).thenReturn(email);

        return jwt;
    }
}
