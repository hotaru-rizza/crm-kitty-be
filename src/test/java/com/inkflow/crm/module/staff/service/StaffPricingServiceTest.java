package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.ArtistServicePricing;
import com.inkflow.crm.domain.entity.Service;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.ArtistServicePricingRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.service.service.ServiceLookup;
import com.inkflow.crm.support.AuditMocks;
import com.inkflow.crm.module.staff.dto.StaffServiceDto;
import com.inkflow.crm.module.staff.dto.UpdateStaffServicesRequest;
import com.inkflow.crm.module.staff.mapper.StaffPricingMapper;
import com.inkflow.crm.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffPricingServiceTest {

    @Mock
    private StaffLookup staffLookup;

    @Mock
    private ServiceLookup serviceLookup;

    @Mock
    private ArtistServicePricingRepository artistServicePricingRepository;

    @Mock
    private StaffPricingMapper staffPricingMapper;

    @Mock
    private EntityManager entityManager;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @Captor
    private ArgumentCaptor<ArtistServicePricing> pricingCaptor;

    @InjectMocks
    private StaffPricingService staffPricingService;

    @BeforeEach
    void stubAudit() {
        AuditMocks.stubLabelFormatter(auditLabelFormatter);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreatePricingWhenAddingServiceToStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticate(tenantId);

        Staff staff = Staff.builder().id(staffId).build();
        Service service = Service.builder().id(serviceId).price(BigDecimal.valueOf(200)).duration(60).build();
        ArtistServicePricing pricing = ArtistServicePricing.builder()
                .staff(staff)
                .service(service)
                .price(BigDecimal.valueOf(250))
                .duration(90)
                .build();
        StaffServiceDto dto = StaffServiceDto.builder().serviceId(serviceId).title("Tattoo").build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);
        when(serviceLookup.require(tenantId, serviceId)).thenReturn(service);
        when(artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId)).thenReturn(Optional.empty());
        when(staffPricingMapper.toEntity(staff, service, BigDecimal.valueOf(250), 90)).thenReturn(pricing);
        when(artistServicePricingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffPricingMapper.toDto(any())).thenReturn(dto);

        StaffServiceDto result = staffPricingService.addServiceToStaff(
                staffId, serviceId, BigDecimal.valueOf(250), 90);

        assertEquals(serviceId, result.getServiceId());
        verify(artistServicePricingRepository).save(pricingCaptor.capture());
        assertEquals(BigDecimal.valueOf(250), pricingCaptor.getValue().getPrice());
        assertEquals(90, pricingCaptor.getValue().getDuration());
    }

    @Test
    void shouldRejectDuplicateAssignmentWhenAddingServiceToStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticate(tenantId);

        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(serviceLookup.require(tenantId, serviceId)).thenReturn(Service.builder().id(serviceId).build());
        when(artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId))
                .thenReturn(Optional.of(ArtistServicePricing.builder().build()));

        assertThrows(BusinessRuleException.class,
                () -> staffPricingService.addServiceToStaff(staffId, serviceId, null, null));
        verify(artistServicePricingRepository, never()).save(any());
    }

    @Test
    void shouldDeleteAssignmentWhenRemovingServiceFromStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticate(tenantId);

        ArtistServicePricing pricing = ArtistServicePricing.builder()
                .service(Service.builder().id(serviceId).title("Consultation").build())
                .build();
        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId))
                .thenReturn(Optional.of(pricing));

        staffPricingService.removeServiceFromStaff(staffId, serviceId);

        verify(artistServicePricingRepository).delete(pricing);
    }

    @Test
    void shouldRejectRemovalWhenServiceNotAssigned() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticate(tenantId);

        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId))
                .thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class,
                () -> staffPricingService.removeServiceFromStaff(staffId, serviceId));
        verify(artistServicePricingRepository, never()).delete(any());
    }

    @Test
    void shouldFilterDeletedServicesWhenListingStaffServices() {
        UUID staffId = UUID.randomUUID();
        UUID activeServiceId = UUID.randomUUID();
        UUID deletedServiceId = UUID.randomUUID();

        Service activeService = Service.builder().id(activeServiceId).deletedAt(null).build();
        Service deletedService = Service.builder().id(deletedServiceId).deletedAt(Instant.now()).build();

        ArtistServicePricing activePricing = ArtistServicePricing.builder()
                .service(activeService)
                .price(BigDecimal.valueOf(100))
                .build();
        ArtistServicePricing deletedPricing = ArtistServicePricing.builder()
                .service(deletedService)
                .price(BigDecimal.valueOf(50))
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(artistServicePricingRepository.findByStaffId(staffId))
                .thenReturn(List.of(activePricing, deletedPricing));
        when(staffPricingMapper.toDto(activePricing))
                .thenReturn(StaffServiceDto.builder().serviceId(activeServiceId).build());

        List<StaffServiceDto> result = staffPricingService.getStaffServices(staffId);

        assertEquals(1, result.size());
        assertEquals(activeServiceId, result.getFirst().getServiceId());
    }

    @Test
    void shouldReplaceAllAssignmentsWhenUpdatingStaffServices() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticate(tenantId);

        Staff staff = Staff.builder().id(staffId).build();
        Service service = Service.builder().id(serviceId).build();
        ArtistServicePricing pricing = ArtistServicePricing.builder()
                .staff(staff)
                .service(service)
                .price(BigDecimal.valueOf(300))
                .duration(120)
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);
        when(serviceLookup.require(tenantId, serviceId)).thenReturn(service);
        when(staffPricingMapper.toEntity(staff, service, BigDecimal.valueOf(300), 120)).thenReturn(pricing);
        when(artistServicePricingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffPricingMapper.toDto(any()))
                .thenReturn(StaffServiceDto.builder().serviceId(serviceId).build());

        UpdateStaffServicesRequest request = UpdateStaffServicesRequest.builder()
                .services(List.of(UpdateStaffServicesRequest.ServiceAssignment.builder()
                        .serviceId(serviceId)
                        .customPrice(BigDecimal.valueOf(300))
                        .customDuration(120)
                        .build()))
                .build();

        List<StaffServiceDto> result = staffPricingService.updateStaffServices(staffId, request);

        verify(artistServicePricingRepository).deleteByStaffId(staffId);
        verify(entityManager).flush();
        verify(artistServicePricingRepository).save(pricingCaptor.capture());
        assertEquals(BigDecimal.valueOf(300), pricingCaptor.getValue().getPrice());
        assertEquals(120, pricingCaptor.getValue().getDuration());
        assertEquals(1, result.size());
    }

    @Test
    void shouldUpdateOnlyProvidedPricingFieldsWhenUpdating() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticate(tenantId);

        ArtistServicePricing pricing = ArtistServicePricing.builder()
                .price(BigDecimal.valueOf(100))
                .duration(60)
                .service(Service.builder().id(serviceId).title("Consultation").build())
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId))
                .thenReturn(Optional.of(pricing));
        when(artistServicePricingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffPricingMapper.toDto(any()))
                .thenReturn(StaffServiceDto.builder().serviceId(serviceId).build());

        staffPricingService.updateStaffServicePricing(staffId, serviceId, BigDecimal.valueOf(150), null);

        verify(artistServicePricingRepository).save(pricingCaptor.capture());
        assertEquals(BigDecimal.valueOf(150), pricingCaptor.getValue().getPrice());
        assertEquals(60, pricingCaptor.getValue().getDuration());
    }

    @Test
    void shouldRejectPricingUpdateWhenServiceNotAssigned() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        authenticate(tenantId);

        when(staffLookup.requireStaff(staffId)).thenReturn(Staff.builder().id(staffId).build());
        when(artistServicePricingRepository.findByStaffIdAndServiceId(staffId, serviceId))
                .thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class,
                () -> staffPricingService.updateStaffServicePricing(
                        staffId, serviceId, BigDecimal.TEN, 30));
        verify(artistServicePricingRepository, never()).save(any());
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
