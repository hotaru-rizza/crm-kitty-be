package com.inkflow.crm.infrastructure.supabase;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

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

    public void syncUserTenantClaims(String authUserId, UUID tenantId, String role) {
        if (!enabled) {
            log.warn("Supabase admin not configured — skipping tenant claims sync for user {}", authUserId);
            return;
        }
        try {
            restClient.put()
                    .uri("/auth/v1/admin/users/{id}", authUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(Map.of(
                            "app_metadata", Map.of(
                                    "tenant_id", tenantId.toString(),
                                    "role", role
                            )
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Synced Supabase tenant claims: authUserId={} tenantId={} role={}", authUserId, tenantId, role);
        } catch (Exception e) {
            log.warn("Failed to sync Supabase tenant claims for auth user {}: {}", authUserId, e.getMessage());
        }
    }

    public void deleteUser(String authUserId) {
        if (!enabled) {
            log.warn("Supabase admin not configured — skipping user deletion for {}", authUserId);
            return;
        }
        try {
            restClient.delete()
                    .uri("/auth/v1/admin/users/{id}", authUserId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + serviceRoleKey)
                    .header("apikey", serviceRoleKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Deleted Supabase auth user {}", authUserId);
        } catch (Exception e) {
            log.error("Failed to delete Supabase auth user {}: {}", authUserId, e.getMessage());
        }
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
