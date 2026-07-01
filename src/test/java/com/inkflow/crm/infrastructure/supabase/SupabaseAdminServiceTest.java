package com.inkflow.crm.infrastructure.supabase;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

class SupabaseAdminServiceTest {

    private static final String BASE_URL = "http://localhost/supabase";

    @Test
    void shouldSkipRevocationWhenSupabaseNotConfigured() {
        SupabaseAdminService service = new SupabaseAdminService("", "");

        assertDoesNotThrow(() -> service.revokeAllSessions("auth-user-123"));
    }

    @Test
    void shouldSkipRevocationWhenOnlyUrlConfigured() {
        SupabaseAdminService service = new SupabaseAdminService(BASE_URL, "");

        assertDoesNotThrow(() -> service.revokeAllSessions("auth-user-789"));
    }

    @Test
    void shouldSkipRevocationWhenOnlyServiceKeyConfigured() {
        SupabaseAdminService service = new SupabaseAdminService("", "service-role-key");

        assertDoesNotThrow(() -> service.revokeAllSessions("auth-user-790"));
    }

    @Test
    void shouldSyncUserTenantClaimsWhenSupabaseRespondsOk() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID tenantId = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/auth/v1/admin/users/auth-user-ok"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("apikey", "service-role-key"))
                .andExpect(header("Authorization", "Bearer service-role-key"))
                .andRespond(withNoContent());

        SupabaseAdminService service = new SupabaseAdminService(BASE_URL, "service-role-key");
        ReflectionTestUtils.setField(service, "restClient", builder.build());

        assertDoesNotThrow(() -> service.syncUserTenantClaims("auth-user-ok", tenantId, "owner"));
        server.verify();
    }

    @Test
    void shouldRevokeSessionsWhenSupabaseRespondsOk() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/auth/v1/admin/users/auth-user-ok/sessions"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("apikey", "service-role-key"))
                .andExpect(header("Authorization", "Bearer service-role-key"))
                .andRespond(withNoContent());

        SupabaseAdminService service = new SupabaseAdminService(BASE_URL, "service-role-key");
        ReflectionTestUtils.setField(service, "restClient", builder.build());

        assertDoesNotThrow(() -> service.revokeAllSessions("auth-user-ok"));
        server.verify();
    }

    @Test
    void shouldSwallowHttpFailureWhenSupabaseConfigured() {
        SupabaseAdminService service = new SupabaseAdminService("http://127.0.0.1:1", "service-role-key");

        assertDoesNotThrow(() -> service.revokeAllSessions("auth-user-456"));
    }
}
