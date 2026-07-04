package com.inkflow.crm.module.location.service;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.location.dto.AssignStaffRequest;
import com.inkflow.crm.module.location.dto.CreateLocationRequest;
import com.inkflow.crm.module.location.dto.UpdateLocationRequest;
import com.inkflow.crm.module.location.mapper.LocationMapper;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LocationMapper locationMapper;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @InjectMocks
    private LocationService locationService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deleteLocation_softDeletesWhenOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateOwner(tenantId);

        Location location = Location.builder()
                .id(locationId)
                .tenantId(tenantId)
                .name("Studio")
                .build();

        when(locationRepository.findByIdAndDeletedAtIsNull(locationId))
                .thenReturn(Optional.of(location));
        when(locationRepository.countByDeletedAtIsNull()).thenReturn(2L);

        locationService.deleteLocation(locationId);

        assertNotNull(location.getDeletedAt());
        verify(locationRepository).save(location);
    }

    @Test
    void createLocation_rejectsArtistRole() {
        UUID tenantId = UUID.randomUUID();
        authenticateArtist(tenantId);

        CreateLocationRequest request = CreateLocationRequest.builder()
                .name("Studio")
                .address("Kyiv")
                .color("#6366f1")
                .build();

        assertThrows(AccessDeniedException.class, () -> locationService.createLocation(request));
    }

    @Test
    void deleteLocation_rejectsAdminRole() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateAdmin(tenantId);

        assertThrows(AccessDeniedException.class, () -> locationService.deleteLocation(locationId));
    }

    @Test
    void getLocationById_rejectsMissingLocation() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateOwner(tenantId);

        when(locationRepository.findByIdAndDeletedAtIsNull(locationId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> locationService.getLocationById(locationId));
    }

    @Test
    void updateLocation_rejectsArtistRole() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateArtist(tenantId);

        UpdateLocationRequest request = UpdateLocationRequest.builder()
                .name("Updated Studio")
                .build();

        assertThrows(AccessDeniedException.class, () -> locationService.updateLocation(locationId, request));
    }

    @Test
    void updateLocation_rejectsForeignTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateAdmin(tenantId);

        when(locationRepository.findByIdAndDeletedAtIsNull(locationId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> locationService.updateLocation(locationId, UpdateLocationRequest.builder().name("Updated").build()));
    }

    @Test
    void deleteLocation_rejectsLastLocation() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateOwner(tenantId);

        Location location = Location.builder()
                .id(locationId)
                .tenantId(tenantId)
                .name("Studio")
                .build();

        when(locationRepository.findByIdAndDeletedAtIsNull(locationId))
                .thenReturn(Optional.of(location));
        when(locationRepository.countByDeletedAtIsNull()).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () -> locationService.deleteLocation(locationId));
        verify(locationRepository, never()).save(any());
    }

    @Test
    void updateLocation_rejectsDeactivatingLastActiveLocation() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateAdmin(tenantId);

        Location location = Location.builder()
                .id(locationId)
                .tenantId(tenantId)
                .name("Studio")
                .isActive(true)
                .build();

        when(locationRepository.findByIdAndDeletedAtIsNull(locationId))
                .thenReturn(Optional.of(location));
        when(locationRepository.countByIsActiveAndDeletedAtIsNull(true)).thenReturn(1L);

        UpdateLocationRequest request = UpdateLocationRequest.builder()
                .name("Studio")
                .isActive(false)
                .build();

        assertThrows(BusinessRuleException.class, () -> locationService.updateLocation(locationId, request));
        verify(locationRepository, never()).save(any());
    }

    @Test
    void deleteLocation_rejectsMissingLocation() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateOwner(tenantId);

        when(locationRepository.findByIdAndDeletedAtIsNull(locationId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> locationService.deleteLocation(locationId));
        verify(locationRepository, never()).save(any());
    }

    @Test
    void assignStaff_rejectsArtistRole() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateArtist(tenantId);

        AssignStaffRequest request = AssignStaffRequest.builder()
                .staffIds(List.of(UUID.randomUUID()))
                .build();

        assertThrows(AccessDeniedException.class, () -> locationService.assignStaff(locationId, request));
    }

    @Test
    void assignStaff_rejectsForeignTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticateAdmin(tenantId);

        when(locationRepository.findByIdAndDeletedAtIsNull(locationId))
                .thenReturn(Optional.empty());

        AssignStaffRequest request = AssignStaffRequest.builder()
                .staffIds(List.of(UUID.randomUUID()))
                .build();

        assertThrows(ResourceNotFoundException.class, () -> locationService.assignStaff(locationId, request));
        verify(staffRepository, never()).findByIdInAndDeletedAtIsNull(any());
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
