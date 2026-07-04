package com.inkflow.crm.security;

import com.inkflow.crm.security.support.TenantRepositoryScanSupport;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Spring Data derived {@code deleteBy*} / {@code updateBy*} methods generate bulk SQL that bypasses
 * Hibernate {@code @Filter}. Use {@code findBy...()} → {@code deleteAll(list)} or load → mutate → {@code save()}.
 */
class DerivedBulkMutationSafetyTest {

    @Test
    void repositoriesMustNotExposeDerivedBulkDeleteOrUpdateMethods() {
        List<String> violations = new ArrayList<>();

        for (Class<?> repositoryClass : TenantRepositoryScanSupport.allJpaRepositoryInterfaces()) {
            for (Method method : repositoryClass.getDeclaredMethods()) {
                if (!isDerivedBulkMutation(method)) {
                    continue;
                }
                violations.add(repositoryClass.getName() + "." + method.getName());
            }
        }

        if (!violations.isEmpty()) {
            fail("Derived bulk mutation methods detected (use find → deleteAll / load → save instead):\n- "
                    + String.join("\n- ", violations));
        }
    }

    private static boolean isDerivedBulkMutation(Method method) {
        if (method.getAnnotation(Query.class) != null) {
            return false;
        }
        String name = method.getName();
        return name.startsWith("deleteBy") || name.startsWith("updateBy");
    }
}
