package com.inkflow.crm.infrastructure.supabase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class SupabaseAdminService {

    private final RestClient restClient;
    private final String serviceRoleKey;
    private final boolean enabled;

    public SupabaseAdminService(
            @Value("${supabase.url:}") String supabaseUrl,
            @Value("${supabase.service-role-key:}") String serviceRoleKey
    ) {
        this.serviceRoleKey = serviceRoleKey;
        this.enabled = !supabaseUrl.isBlank() && !serviceRoleKey.isBlank();
        this.restClient = RestClient.builder()
                .baseUrl(supabaseUrl.isBlank() ? "https://placeholder.supabase.co" : supabaseUrl)
                .build();
    }

    public void revokeAllSessions(String authUserId) {
        if (!enabled) {
            log.warn("Supabase admin not configured — skipping session revocation for user {}", authUserId);
            return;
        }
        try {
            restClient.delete()
                    .uri("/auth/v1/admin/users/{id}/sessions", authUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Revoked all sessions for auth user {}", authUserId);
        } catch (Exception e) {
            log.error("Failed to revoke sessions for auth user {}: {}", authUserId, e.getMessage());
        }
    }
}
