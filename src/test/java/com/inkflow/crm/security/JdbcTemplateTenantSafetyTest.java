package com.inkflow.crm.security;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * {@link JdbcTemplate} mutations bypass Hibernate {@code @Filter}.
 * Only {@code @BypassTenantFilter} services (e.g. DevAdmin) may use raw SQL mutations.
 */
class JdbcTemplateTenantSafetyTest {

    private static final String ROOT = "com.inkflow.crm";

    private static final Set<String> JDBC_MUTATION_BYPASS_OWNERS = Set.of(
            "DevAdminService"
    );

    private static final Set<String> JDBC_MUTATION_METHODS = Set.of(
            "update",
            "batchUpdate",
            "execute"
    );

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void moduleCodeMustNotUseJdbcTemplateMutationsOutsideBypassServices() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..module..")
                .should(notCallJdbcTemplateMutations())
                .because("JdbcTemplate UPDATE/DELETE bypasses tenant filter — "
                        + "use repository with explicit tenantId or @BypassTenantFilter service");

        rule.check(importedClasses);
    }

    private static ArchCondition<com.tngtech.archunit.core.domain.JavaClass> notCallJdbcTemplateMutations() {
        return new ArchCondition<>("not call JdbcTemplate mutation methods") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaClass item, ConditionEvents events) {
                if (JDBC_MUTATION_BYPASS_OWNERS.contains(item.getSimpleName())) {
                    return;
                }
                for (JavaCall<?> call : item.getMethodCallsFromSelf()) {
                    if (!JDBC_MUTATION_METHODS.contains(call.getName())) {
                        continue;
                    }
                    if (!call.getTargetOwner().isAssignableTo(JdbcTemplate.class)) {
                        continue;
                    }
                    String message = String.format(
                            "%s calls JdbcTemplate.%s() — raw SQL mutation outside bypass allowlist",
                            call.getOriginOwner().getFullName(),
                            call.getName()
                    );
                    events.add(SimpleConditionEvent.violated(call, message));
                }
            }
        };
    }
}
