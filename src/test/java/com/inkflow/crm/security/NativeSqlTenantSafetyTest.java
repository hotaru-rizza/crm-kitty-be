package com.inkflow.crm.security;

import com.inkflow.crm.security.support.TenantRepositoryScanSupport;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Native SQL bypasses Hibernate {@code @Filter}. Every native query on tenant-scoped tables
 * must include an explicit {@code tenant_id} predicate, unless allowlisted (global/scheduler).
 */
class NativeSqlTenantSafetyTest {

    private static final Set<String> TENANT_SCOPED_TABLES = Set.of(
            "transactions",
            "clients",
            "staff",
            "appointments",
            "projects",
            "locations",
            "email_template",
            "email_message",
            "gallery_photos",
            "requests",
            "notifications"
    );

    /** Methods intentionally cross-tenant (scheduler, outbox worker). */
    private static final Set<String> CROSS_TENANT_NATIVE_METHODS = Set.of(
            "com.inkflow.crm.domain.repository.EmailMessageRepository.findPendingForProcessing"
    );

    @Test
    void nativeQueriesOnTenantTablesMustIncludeTenantIdOrBeAllowlisted() {
        List<String> violations = new ArrayList<>();

        for (Class<?> repositoryClass : TenantRepositoryScanSupport.allJpaRepositoryInterfaces()) {
            if (TenantRepositoryScanSupport.GLOBAL_NATIVE_REPOSITORIES.contains(repositoryClass.getName())) {
                continue;
            }
            for (Method method : repositoryClass.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query == null || !query.nativeQuery()) {
                    continue;
                }

                String methodKey = repositoryClass.getName() + "." + method.getName();
                String sql = normalize(query.value());

                if (CROSS_TENANT_NATIVE_METHODS.contains(methodKey)) {
                    continue;
                }

                if (touchesTenantScopedTable(sql) && !sql.contains("tenant_id")) {
                    violations.add(methodKey + " — native SQL on tenant table without tenant_id");
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Unsafe native SQL detected:\n- " + String.join("\n- ", violations));
        }
    }

    private static boolean touchesTenantScopedTable(String sql) {
        return TENANT_SCOPED_TABLES.stream().anyMatch(table -> sql.contains(" " + table + " ")
                || sql.contains(" " + table + "\n")
                || sql.startsWith(table + " ")
                || sql.contains("from " + table));
    }

    private static String normalize(String sql) {
        return sql.toLowerCase().replace('\r', ' ').replace('\n', ' ');
    }
}
