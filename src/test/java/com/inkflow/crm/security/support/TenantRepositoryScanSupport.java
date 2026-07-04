package com.inkflow.crm.security.support;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Discovers {@link JpaRepository} interfaces under {@code com.inkflow.crm} for tenant safety CI tests.
 */
public final class TenantRepositoryScanSupport {

    private static final String ROOT = "com.inkflow.crm";

    /** Repositories for global / non-tenant-scoped data — excluded from tenant isolation checks. */
    public static final Set<String> GLOBAL_REPOSITORIES = Set.of(
            "TenantRepository",
            "TattooRepository",
            "TattooStyleRepository",
            "AiGenerationRepository",
            "SchedulerRunRepository",
            "ConsumerUserRepository"
    );

    /** Repositories whose native SQL never touches tenant-scoped tables. */
    public static final Set<String> GLOBAL_NATIVE_REPOSITORIES = Set.of(
            "com.inkflow.crm.module.catalog.repository.TattooRepository",
            "com.inkflow.crm.module.catalog.repository.TattooStyleRepository"
    );

    private static volatile List<Class<?>> cachedAllRepositories;
    private static volatile List<Class<?>> cachedTenantScopedRepositories;

    private TenantRepositoryScanSupport() {
    }

    public static List<Class<?>> allJpaRepositoryInterfaces() {
        if (cachedAllRepositories == null) {
            cachedAllRepositories = loadRepositories(false);
        }
        return cachedAllRepositories;
    }

    public static List<Class<?>> tenantScopedJpaRepositoryInterfaces() {
        if (cachedTenantScopedRepositories == null) {
            cachedTenantScopedRepositories = loadRepositories(true);
        }
        return cachedTenantScopedRepositories;
    }

    private static List<Class<?>> loadRepositories(boolean tenantScopedOnly) {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        List<Class<?>> repositories = new ArrayList<>();
        imported.stream()
                .filter(javaClass -> javaClass.isInterface())
                .filter(javaClass -> javaClass.isAssignableTo(JpaRepository.class))
                .filter(javaClass -> javaClass.getSimpleName().endsWith("Repository"))
                .filter(javaClass -> !tenantScopedOnly
                        || !GLOBAL_REPOSITORIES.contains(javaClass.getSimpleName()))
                .forEach(javaClass -> repositories.add(javaClass.reflect()));
        repositories.sort(Comparator.comparing(Class::getName));
        return repositories;
    }
}
