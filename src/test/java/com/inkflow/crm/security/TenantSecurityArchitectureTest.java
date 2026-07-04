package com.inkflow.crm.security;

import com.inkflow.crm.security.support.TenantRepositoryScanSupport;
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
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * CI guardrails for tenant isolation: blocks repository calls that bypass Hibernate {@code @Filter}.
 */
class TenantSecurityArchitectureTest {

    private static final String ROOT = "com.inkflow.crm";

    /** Intentionally cross-tenant bulk repo methods (must use explicit {@code @Query}). */
    private static final Set<String> ALLOWLISTED_REPO_BULK_METHODS = Set.of(
            "deleteByCreatedAtBefore"
    );

    private static JavaClasses importedClasses;

    @BeforeAll
    static void importClasses() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void servicesMustNotCallUnsafeRepositoryFindById() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..module..", "..common.scheduler..", "..common..")
                .and().haveSimpleNameNotEndingWith("Repository")
                .should(notCallUnsafeRepositoryMethod("findById"))
                .because("JpaRepository.findById() bypasses Hibernate tenant filter — "
                        + "use findByIdAndDeletedAtIsNull, findByIdAndTenantId, or scoped JPQL");

        rule.check(importedClasses);
    }

    @Test
    void servicesMustNotCallUnsafeRepositoryFindAllById() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..module..", "..common.scheduler..", "..common..")
                .and().haveSimpleNameNotEndingWith("Repository")
                .should(notCallUnsafeRepositoryMethod("findAllById"))
                .because("JpaRepository.findAllById() bypasses Hibernate tenant filter — "
                        + "use findByIdInAndDeletedAtIsNull or scoped queries");

        rule.check(importedClasses);
    }

    @Test
    void servicesMustNotCallUnsafeRepositoryDeleteById() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..module..", "..common.scheduler..", "..common..")
                .and().haveSimpleNameNotEndingWith("Repository")
                .should(notCallUnsafeRepositoryMethod("deleteById"))
                .because("JpaRepository.deleteById() bypasses Hibernate tenant filter — "
                        + "use findByIdAndDeletedAtIsNull → delete(entity)");

        rule.check(importedClasses);
    }

    @Test
    void servicesMustNotCallDerivedBulkDeleteOrUpdateOnRepositories() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..module..", "..common.scheduler..", "..common..")
                .and().haveSimpleNameNotEndingWith("Repository")
                .should(notCallDerivedBulkMutationOnRepository())
                .because("derived deleteBy* / updateBy* bypass Hibernate tenant filter — "
                        + "use find → deleteAll or load → save, or explicit tenantId in @Query");

        rule.check(importedClasses);
    }

    private static ArchCondition<com.tngtech.archunit.core.domain.JavaClass> notCallUnsafeRepositoryMethod(
            String methodName
    ) {
        return new ArchCondition<>("not call " + methodName + " on tenant-scoped repositories") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaClass item, ConditionEvents events) {
                for (JavaCall<?> call : item.getMethodCallsFromSelf()) {
                    if (!methodName.equals(call.getName())) {
                        continue;
                    }
                    if (!isTenantScopedRepositoryCall(call)) {
                        continue;
                    }
                    String message = String.format(
                            "%s calls %s.%s() — unsafe for tenant-scoped entities",
                            call.getOriginOwner().getFullName(),
                            call.getTargetOwner().getSimpleName(),
                            methodName
                    );
                    events.add(SimpleConditionEvent.violated(call, message));
                }
            }
        };
    }

    private static ArchCondition<com.tngtech.archunit.core.domain.JavaClass> notCallDerivedBulkMutationOnRepository() {
        return new ArchCondition<>("not call derived bulk deleteBy* / updateBy* on tenant-scoped repositories") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaClass item, ConditionEvents events) {
                for (JavaCall<?> call : item.getMethodCallsFromSelf()) {
                    if (!isTenantScopedRepositoryCall(call)) {
                        continue;
                    }
                    String methodName = call.getName();
                    if (ALLOWLISTED_REPO_BULK_METHODS.contains(methodName)) {
                        continue;
                    }
                    if (!methodName.startsWith("deleteBy") && !methodName.startsWith("updateBy")) {
                        continue;
                    }
                    String message = String.format(
                            "%s calls %s.%s() — derived bulk mutation bypasses tenant filter",
                            call.getOriginOwner().getFullName(),
                            call.getTargetOwner().getSimpleName(),
                            methodName
                    );
                    events.add(SimpleConditionEvent.violated(call, message));
                }
            }
        };
    }

    private static boolean isTenantScopedRepositoryCall(JavaCall<?> call) {
        if (!call.getTargetOwner().isAssignableTo(JpaRepository.class)) {
            return false;
        }
        return !TenantRepositoryScanSupport.GLOBAL_REPOSITORIES.contains(call.getTargetOwner().getSimpleName());
    }
}
