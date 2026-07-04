package com.inkflow.crm.security;

import com.inkflow.crm.security.support.TenantRepositoryScanSupport;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Bulk JPQL {@code @Modifying} UPDATE/DELETE bypasses Hibernate {@code @Filter}.
 * Tenant-scoped entities must include explicit {@code tenantId} (or be allowlisted).
 */
class BulkJpqlTenantSafetyTest {

    private static final Set<String> TENANT_SCOPED_JPQL_ENTITIES = Set.of(
            "Client",
            "Staff",
            "Transaction",
            "RolePermission",
            "Appointment",
            "Project",
            "Location",
            "EmailTemplate",
            "Notification",
            "Request",
            "GalleryPhoto"
    );

    /** Intentionally cross-tenant or scoped by globally unique key. */
    private static final Set<String> ALLOWLISTED_METHODS = Set.of(
            "com.inkflow.crm.domain.repository.AuditLogRepository.deleteByCreatedAtBefore",
            "com.inkflow.crm.module.notification.repository.NotificationRepository.markAllAsRead"
    );

    @Test
    void modifyingJpqlOnTenantEntitiesMustIncludeTenantIdOrBeAllowlisted() {
        List<String> violations = new ArrayList<>();

        for (Class<?> repositoryClass : TenantRepositoryScanSupport.tenantScopedJpaRepositoryInterfaces()) {
            for (Method method : repositoryClass.getDeclaredMethods()) {
                Modifying modifying = method.getAnnotation(Modifying.class);
                Query query = method.getAnnotation(Query.class);
                if (modifying == null || query == null || query.nativeQuery()) {
                    continue;
                }

                String methodKey = repositoryClass.getName() + "." + method.getName();
                if (ALLOWLISTED_METHODS.contains(methodKey)) {
                    continue;
                }

                String jpql = normalize(query.value());
                if (!isBulkMutation(jpql)) {
                    continue;
                }

                if (touchesTenantScopedEntity(jpql) && !jpql.contains("tenantid")) {
                    violations.add(methodKey + " — bulk JPQL on tenant entity without tenantId");
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Unsafe bulk JPQL detected:\n- " + String.join("\n- ", violations));
        }
    }

    private static boolean isBulkMutation(String jpql) {
        return jpql.startsWith("update ") || jpql.startsWith("delete ");
    }

    private static boolean touchesTenantScopedEntity(String jpql) {
        return TENANT_SCOPED_JPQL_ENTITIES.stream()
                .anyMatch(entity -> jpql.contains(" " + entity.toLowerCase() + " ")
                        || jpql.contains(" " + entity.toLowerCase() + "\n")
                        || jpql.startsWith(entity.toLowerCase() + " "));
    }

    private static String normalize(String jpql) {
        return jpql.toLowerCase().replace('\r', ' ').replace('\n', ' ');
    }
}
