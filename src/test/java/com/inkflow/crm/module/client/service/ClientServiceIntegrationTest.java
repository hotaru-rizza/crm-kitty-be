package com.inkflow.crm.module.client.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.client.dto.CreateClientRequest;
import com.inkflow.crm.module.client.dto.UpdateClientRequest;
import com.inkflow.crm.support.IntegrationTest;
import com.inkflow.crm.support.IntegrationTestData;
import com.inkflow.crm.support.IntegrationTestData.TenantBundle;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
@Transactional
class ClientServiceIntegrationTest {

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

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void createClient_persistsNormalizedPhone() {
        TenantBundle bundle = seedTenant();
        SecurityTestSupport.authenticate(bundle.owner());

        var created = clientService.createClient(CreateClientRequest.builder()
                .firstName("New")
                .lastName("Client")
                .email("new.client@test.com")
                .phone("+38 (050) 111-22-33")
                .build());

        assertEquals("+380501112233", created.getPhone());

        var persisted = clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                created.getId(), bundle.tenant().getId()).orElseThrow();
        assertEquals("+380501112233", persisted.getPhone());
        assertEquals(false, persisted.isBlacklisted());
        assertEquals(bundle.tenant().getId(), persisted.getTenantId());

        assertEquals(2, clientRepository.findAll().stream()
                .filter(c -> bundle.tenant().getId().equals(c.getTenantId()) && c.getDeletedAt() == null)
                .count());
    }

    @Test
    void createClient_rejectsDuplicatePhoneInSameTenant() {
        TenantBundle bundle = seedTenant();
        SecurityTestSupport.authenticate(bundle.owner());

        String phone = "+380501112233";
        clientService.createClient(CreateClientRequest.builder()
                .firstName("First")
                .lastName("Client")
                .email("first.client@test.com")
                .phone(phone)
                .build());

        assertThrows(BusinessRuleException.class, () -> clientService.createClient(CreateClientRequest.builder()
                .firstName("Second")
                .lastName("Client")
                .email("second.client@test.com")
                .phone(phone)
                .build()));

        assertEquals(2, clientRepository.findAll().stream()
                .filter(c -> bundle.tenant().getId().equals(c.getTenantId()) && c.getDeletedAt() == null)
                .count());
    }

    @Test
    void getClientById_rejectsClientFromAnotherTenant() {
        TenantBundle tenantA = seedTenant();
        TenantBundle tenantB = seedTenant();

        SecurityTestSupport.authenticate(tenantA.owner());

        assertThrows(
                ResourceNotFoundException.class,
                () -> clientService.getClientById(tenantB.client().getId())
        );

        assertTrue(clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                tenantB.client().getId(), tenantB.tenant().getId()).isPresent());
    }

    @Test
    void updateClient_persistsNormalizedPhoneInDb() {
        TenantBundle bundle = seedTenant();
        SecurityTestSupport.authenticate(bundle.owner());

        var updated = clientService.updateClient(bundle.client().getId(), UpdateClientRequest.builder()
                .phone("+38 (067) 999-88-77")
                .build());

        assertEquals("+380679998877", updated.getPhone());

        var persisted = clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                bundle.client().getId(), bundle.tenant().getId()).orElseThrow();
        assertEquals("+380679998877", persisted.getPhone());
    }

    @Test
    void deleteClient_softDeletesRecord() {
        TenantBundle bundle = seedTenant();
        SecurityTestSupport.authenticate(bundle.owner());

        clientService.deleteClient(bundle.client().getId());

        assertTrue(clientRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                bundle.client().getId(), bundle.tenant().getId()).isEmpty());

        var softDeleted = clientRepository.findById(bundle.client().getId()).orElseThrow();
        assertNotNull(softDeleted.getDeletedAt());
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
