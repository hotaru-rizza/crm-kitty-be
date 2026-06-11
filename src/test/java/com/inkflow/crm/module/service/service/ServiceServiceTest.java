package com.inkflow.crm.module.service.service;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.repository.ArtistServicePricingRepository;
import com.inkflow.crm.domain.repository.ServiceRepository;
import com.inkflow.crm.module.service.dto.CreateServiceRequest;
import com.inkflow.crm.module.service.dto.ServiceDto;
import com.inkflow.crm.module.service.dto.UpdateServiceRequest;
import com.inkflow.crm.module.service.mapper.ServiceMapper;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private ServiceService serviceService;

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
