package com.inkflow.crm.module.location.service;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TransactionRepository;
import com.inkflow.crm.module.location.dto.CreateLocationRequest;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.of(location));

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

        when(locationRepository.findByIdAndTenantIdAndDeletedAtIsNull(locationId, tenantId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> locationService.getLocationById(locationId));
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
