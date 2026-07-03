package com.inkflow.crm.module.finance.service;

import com.inkflow.crm.domain.repository.TransactionCategoryConfigRepository;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
@Transactional
class CategoryConfigServiceIntegrationTest {

    @Autowired
    private CategoryConfigService categoryConfigService;

    @Autowired
    private TransactionCategoryConfigRepository categoryConfigRepository;

    @Autowired
    private com.inkflow.crm.domain.repository.TenantRepository tenantRepository;

    @Autowired
    private com.inkflow.crm.domain.repository.StaffRepository staffRepository;

    @Autowired
    private com.inkflow.crm.domain.repository.ClientRepository clientRepository;

    @Autowired
    private com.inkflow.crm.domain.repository.ServiceRepository serviceRepository;

    @Autowired
    private com.inkflow.crm.domain.repository.LocationRepository locationRepository;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void ensureDefaults_seedsCategoriesForNewTenant() {
        TenantBundle bundle = seedTenant();

        categoryConfigService.ensureDefaults(bundle.tenant().getId());

        assertEquals(8, categoryConfigRepository.findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc().size());
    }

    @Test
    void ensureDefaults_isIdempotentForSameTenant() {
        TenantBundle bundle = seedTenant();
        UUID tenantId = bundle.tenant().getId();

        categoryConfigService.ensureDefaults(tenantId);
        categoryConfigService.ensureDefaults(tenantId);

        assertEquals(8, categoryConfigRepository.findByDeletedAtIsNullOrderByIsDefaultDescLabelAsc().size());
    }

    @Test
    void upsert_createsCustomCategoryInDb() {
        TenantBundle bundle = seedTenant();
        SecurityTestSupport.authenticate(bundle.owner());

        var created = categoryConfigService.upsert("custom_key", "Custom Label", "#111111", "EXPENSE");

        var persisted = categoryConfigRepository.findById(created.getId()).orElseThrow();
        assertEquals(bundle.tenant().getId(), persisted.getTenantId());
        assertEquals("custom_key", persisted.getCategoryKey());
        assertEquals("Custom Label", persisted.getLabel());
        assertEquals("#111111", persisted.getColor());
        assertEquals("EXPENSE", persisted.getPlType());
        assertTrue(persisted.getIsActive());
    }

    @Test
    void delete_softDeletesCustomCategoryInDb() {
        TenantBundle bundle = seedTenant();
        SecurityTestSupport.authenticate(bundle.owner());

        var created = categoryConfigService.upsert("custom_key", "Custom Label", "#111111", "EXPENSE");

        categoryConfigService.delete(created.getId());

        var deleted = categoryConfigRepository.findById(created.getId()).orElseThrow();
        assertNotNull(deleted.getDeletedAt());
    }

    private TenantBundle seedTenant() {
        return IntegrationTestData.seedTenant(
                tenantRepository,
                staffRepository,
                clientRepository,
                serviceRepository,
                locationRepository
        );
    }
}
