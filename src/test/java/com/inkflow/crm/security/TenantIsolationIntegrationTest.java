package com.inkflow.crm.security;

import com.inkflow.crm.common.dto.PageRequest;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.client.dto.ClientFilterRequest;
import com.inkflow.crm.module.client.service.ClientService;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.PersistenceTestSupport;
import com.inkflow.crm.support.SecurityTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@IntegrationTest
@Transactional
class TenantIsolationIntegrationTest {

    @Autowired
    private ClientService clientService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private LocationRepository locationRepository;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getAllClients_returnsOnlyCurrentTenantClients() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        PersistenceTestSupport.clearPersistenceContext(entityManager);

        SecurityTestSupport.authenticate(tenantA.owner());

        var pageRequest = new PageRequest();
        var filter = new ClientFilterRequest();
        var result = clientService.getAllClients(pageRequest, filter);

        assertEquals(1, result.getData().size());
        assertEquals(tenantA.client().getId(), result.getData().getFirst().getId());
    }

    @Test
    void getClientById_hidesOtherTenantClient() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();
        PersistenceTestSupport.clearPersistenceContext(entityManager);

        SecurityTestSupport.authenticate(tenantA.owner());

        assertEquals(tenantA.client().getId(), clientService.getClientById(tenantA.client().getId()).getId());
        assertThrows(
                ResourceNotFoundException.class,
                () -> clientService.getClientById(tenantB.client().getId())
        );
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
