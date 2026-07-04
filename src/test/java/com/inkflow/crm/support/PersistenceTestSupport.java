package com.inkflow.crm.support;

import jakarta.persistence.EntityManager;

/**
 * Clears the first-level cache so Hibernate {@code @Filter} applies on subsequent loads.
 * Required in {@code @Transactional} integration tests that seed cross-tenant data.
 */
public final class PersistenceTestSupport {

    private PersistenceTestSupport() {
    }

    public static void clearPersistenceContext(EntityManager entityManager) {
        entityManager.flush();
        entityManager.clear();
    }
}
