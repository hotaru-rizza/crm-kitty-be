package com.inkflow.crm.module.service.service;

import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceLookupTest {

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private ServiceLookup serviceLookup;

    @Test
    void require_returnsTenantScopedService() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        Service service = Service.builder().id(serviceId).tenantId(tenantId).title("Tattoo").build();

        when(serviceRepository.findByIdAndTenantIdAndDeletedAtIsNull(serviceId, tenantId))
                .thenReturn(Optional.of(service));

        Service result = serviceLookup.require(tenantId, serviceId);

        assertEquals(serviceId, result.getId());
    }

    @Test
    void require_rejectsMissingService() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        when(serviceRepository.findByIdAndTenantIdAndDeletedAtIsNull(serviceId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> serviceLookup.require(tenantId, serviceId));
    }
}
