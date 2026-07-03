package com.inkflow.crm.module.service.service;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.repository.ArtistServicePricingRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.service.dto.CreateServiceRequest;
import com.inkflow.crm.support.AuditMocks;
import com.inkflow.crm.module.service.dto.ServiceDto;
import com.inkflow.crm.module.service.dto.UpdateServiceRequest;
import com.inkflow.crm.module.service.mapper.ServiceMapper;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private ArtistServicePricingRepository artistServicePricingRepository;

    @Mock
    private ServiceMapper serviceMapper;

    @Mock
    private ServiceLookup serviceLookup;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @InjectMocks
    private ServiceService serviceService;

    @BeforeEach
    void stubAudit() {
        AuditMocks.stubLabelFormatter(auditLabelFormatter);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createService_persistsForAdmin() {
        UUID tenantId = UUID.randomUUID();
        authenticateOwner(tenantId);

        CreateServiceRequest request = CreateServiceRequest.builder()
                .title("Consultation")
                .pricingType("fixed")
                .price(BigDecimal.valueOf(500))
                .duration(30)
                .build();

        Service entity = Service.builder().title("Consultation").build();
        Service saved = Service.builder().id(UUID.randomUUID()).tenantId(tenantId).title("Consultation").build();

        when(serviceMapper.toEntity(request)).thenReturn(entity);
        when(serviceRepository.save(entity)).thenReturn(saved);
        when(serviceMapper.toDto(saved)).thenReturn(ServiceDto.builder().id(saved.getId()).title("Consultation").build());

        ServiceDto result = serviceService.createService(request);

        assertEquals("Consultation", result.getTitle());
        verify(serviceRepository).save(entity);
    }

    @Test
    void createService_rejectsArtistRole() {
        UUID tenantId = UUID.randomUUID();
        authenticateArtist(tenantId);

        CreateServiceRequest request = CreateServiceRequest.builder()
                .title("Consultation")
                .pricingType("fixed")
                .price(BigDecimal.valueOf(500))
                .duration(30)
                .build();

        assertThrows(AccessDeniedException.class, () -> serviceService.createService(request));
    }

    @Test
    void updateService_rejectsArtistRole() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticateArtist(tenantId);

        UpdateServiceRequest request = UpdateServiceRequest.builder()
                .title("Updated")
                .build();

        assertThrows(AccessDeniedException.class, () -> serviceService.updateService(serviceId, request));
    }

    @Test
    void deleteService_rejectsAdminRole() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticateAdmin(tenantId);

        assertThrows(AccessDeniedException.class, () -> serviceService.deleteService(serviceId));
    }

    @Test
    void deleteService_softDeletesForOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticateOwner(tenantId);

        Service service = Service.builder().id(serviceId).tenantId(tenantId).title("Old").build();
        when(serviceLookup.require(tenantId, serviceId)).thenReturn(service);

        serviceService.deleteService(serviceId);

        ArgumentCaptor<Service> captor = ArgumentCaptor.forClass(Service.class);
        verify(serviceRepository).save(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void getServiceById_rejectsMissingService() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticateOwner(tenantId);

        when(serviceLookup.require(tenantId, serviceId))
                .thenThrow(ResourceNotFoundException.service(serviceId.toString()));

        assertThrows(ResourceNotFoundException.class, () -> serviceService.getServiceById(serviceId));
    }

    @Test
    void updateService_rejectsMissingService() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticateAdmin(tenantId);

        when(serviceLookup.require(tenantId, serviceId))
                .thenThrow(ResourceNotFoundException.service(serviceId.toString()));

        UpdateServiceRequest request = UpdateServiceRequest.builder().title("Updated").build();

        assertThrows(ResourceNotFoundException.class, () -> serviceService.updateService(serviceId, request));
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void deleteService_rejectsMissingService() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticateOwner(tenantId);

        when(serviceLookup.require(tenantId, serviceId))
                .thenThrow(ResourceNotFoundException.service(serviceId.toString()));

        assertThrows(ResourceNotFoundException.class, () -> serviceService.deleteService(serviceId));
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void updateService_persistsForAdmin() {
        UUID tenantId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticateAdmin(tenantId);

        Service service = Service.builder().id(serviceId).tenantId(tenantId).title("Old").build();
        Service saved = Service.builder().id(serviceId).tenantId(tenantId).title("Updated").build();

        when(serviceLookup.require(tenantId, serviceId)).thenReturn(service);
        when(serviceRepository.save(service)).thenReturn(saved);
        when(serviceMapper.toDto(saved)).thenReturn(ServiceDto.builder().id(serviceId).title("Updated").build());

        UpdateServiceRequest request = UpdateServiceRequest.builder().title("Updated").build();
        ServiceDto result = serviceService.updateService(serviceId, request);

        assertEquals("Updated", result.getTitle());
        verify(serviceRepository).save(service);
    }

    private void authenticateOwner(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void authenticateAdmin(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.ADMIN)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    private void authenticateArtist(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.ARTIST)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
