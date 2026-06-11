package com.inkflow.crm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "inkflow.openapi", name = "enabled", havingValue = "true")
public class OpenApiConfig {

    public static final String CRM_BEARER = "CrmBearer";
    public static final String CONSUMER_BEARER = "ConsumerBearer";

    @Bean
    public OpenAPI inkflowOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("InkFlow CRM API")
                        .version("1.0.0")
                        .description("""
                        REST API for InkFlow tattoo studio platform.

                        Base path: /api (server context-path).

                        Response envelope: ApiResponse<T> - see docs/API_FORMAT.md.

                        Auth:
                        - CRM (admin mobile / web): Supabase JWT in Authorization Bearer header.
                          Custom claims: tenant_id, role, location_ids (via Supabase hook).
                        - Consumer (B2C app): Supabase JWT for /public/consumer/** routes.
                        - Public reads: GET /public/catalog/** and GET /public/artists/** - no token.
                        """)
                        .contact(new Contact().name("InkFlow").email("dev@inkflow.studio"))
                        .license(new License().name("Proprietary")))
                .addServersItem(new Server().url("/api").description("Current host (context-path included)"))
                .components(new Components()
                        .addSecuritySchemes(CRM_BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Supabase JWT for CRM staff (OWNER/ADMIN/ARTIST)."))
                        .addSecuritySchemes(CONSUMER_BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Supabase JWT for B2C consumer app users.")));
    }

    @Bean
    public GroupedOpenApi consumerApi() {
        return GroupedOpenApi.builder()
                .group("consumer")
                .displayName("Consumer (B2C mobile)")
                .pathsToMatch("/public/**")
                .addOpenApiCustomizer(consumerSecurityCustomizer())
                .build();
    }

    @Bean
    public GroupedOpenApi crmApi() {
        return GroupedOpenApi.builder()
                .group("crm")
                .displayName("CRM (admin mobile)")
                .pathsToMatch("/**")
                .pathsToExclude("/public/**")
                .addOpenApiCustomizer(crmSecurityCustomizer())
                .build();
    }

    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
                .group("all")
                .displayName("All endpoints")
                .pathsToMatch("/**")
                .build();
    }

    private OpenApiCustomizer crmSecurityCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (path.startsWith("/public/") || isPublicSystemPath(path)) {
                return;
            }
            item.readOperations().forEach(op ->
                    op.addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement().addList(CRM_BEARER)));
        });
    }

    private OpenApiCustomizer consumerSecurityCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (isPublicReadPath(path)) {
                return;
            }
            if (path.startsWith("/public/consumer/")) {
                item.readOperations().forEach(op ->
                        op.addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement()
                                .addList(CONSUMER_BEARER)));
            }
        });
    }

    private static boolean isPublicReadPath(String path) {
        return path.startsWith("/public/catalog/") || path.startsWith("/public/artists");
    }

    private static boolean isPublicSystemPath(String path) {
        return path.startsWith("/onboarding")
                || path.startsWith("/payments/monobank/webhook")
                || path.startsWith("/staff/accept-invite")
                || path.startsWith("/staff/invite/info/")
                || path.contains("/google/callback");
    }
}
