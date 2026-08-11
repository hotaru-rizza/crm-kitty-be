package com.inkflow.crm.module.request.service;

import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Request;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.RequestSource;
import com.inkflow.crm.domain.enums.RequestStatus;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.RequestRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.client.mapper.ClientMapper;
import com.inkflow.crm.module.request.dto.CreateRequestRequest;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestServiceTest {

    @Mock
    private RequestRepository requestRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private ClientMapper clientMapper;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private RequestMessageService requestMessageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RequestService requestService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRequest_usesTenantFromSecurityContext() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        CreateRequestRequest req = CreateRequestRequest.builder()
                .source("website")
                .clientName("John Doe")
                .build();

        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        when(locationRepository.findByIsActiveAndDeletedAtIsNull(true)).thenReturn(List.of());

        requestService.createRequest(req);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(captor.capture());

        Request saved = captor.getValue();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(RequestSource.WEBSITE, saved.getSource());
        assertEquals(RequestStatus.NEW, saved.getStatus());
        assertNull(saved.getAssignedStaff());
    }

    @Test
    void createRequest_assignsPrimaryLocationWhenAvailable() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticate(tenantId);

        Location location = Location.builder().id(locationId).name("Studio").build();
        when(locationRepository.findByIsActiveAndDeletedAtIsNull(true)).thenReturn(List.of(location));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        requestService.createRequest(CreateRequestRequest.builder()
                .source("walk_in")
                .clientName("Jane")
                .build());

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(captor.capture());
        assertEquals(locationId, captor.getValue().getLocation().getId());
    }

    @Test
    void createRequest_assignsStaffAndUsesStaffLocation() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticate(tenantId);

        Location location = Location.builder().id(locationId).name("Booth").build();
        Staff staff = Staff.builder()
                .id(staffId)
                .firstName("Olen")
                .lastName("Koval")
                .locations(Set.of(location))
                .build();

        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.of(staff));
        when(requestRepository.save(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        requestService.createRequest(CreateRequestRequest.builder()
                .source("telegram")
                .clientName("Client")
                .assignedStaffId(staffId)
                .tattooTiming("weeks")
                .bodyZones(List.of("front:chest-right"))
                .build());

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(requestRepository).save(captor.capture());
        Request saved = captor.getValue();
        assertEquals(staffId, saved.getAssignedStaff().getId());
        assertEquals(locationId, saved.getLocation().getId());
        assertEquals("weeks", saved.getTattooTiming());
        assertEquals("front:chest-right", saved.getBodyZones());
    }

    private void authenticate(UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
