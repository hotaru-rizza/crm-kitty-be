package com.inkflow.crm.module.auth.service;

import com.inkflow.crm.common.exception.ApiException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.Tenant;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.repository.TenantRepository;
import com.inkflow.crm.module.auth.dto.CurrentUserResponse;
import com.inkflow.crm.module.settings.service.RolePermissionService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private RolePermissionService rolePermissionService;

    @Mock
    private AuthLoginAuditService authLoginAuditService;

    @InjectMocks
    private AuthService authService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_returnsProfileWithPermissions() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String authUserId = UUID.randomUUID().toString();

        authenticate(staffId, tenantId, authUserId, UserRole.OWNER);

        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .email("owner@test.com")
                .firstName("Owner")
                .lastName("User")
                .role(UserRole.OWNER)
                .build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Ink Studio").build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.of(staff));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(rolePermissionService.getGrantedPermissions(tenantId, UserRole.OWNER)).thenReturn(List.of("clients.view_all"));

        CurrentUserResponse response = authService.getCurrentUser();

        assertEquals(staffId, response.getId());
        assertEquals("Ink Studio", response.getTenantName());
        assertEquals(List.of("clients.view_all"), response.getPermissions());
    }

    @Test
    void getCurrentUser_fallsBackToStaffByIdWhenAuthUserIdMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String authUserId = UUID.randomUUID().toString();

        authenticate(staffId, tenantId, authUserId, UserRole.OWNER);

        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .email("owner@test.com")
                .firstName("Owner")
                .lastName("User")
                .role(UserRole.OWNER)
                .build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Ink Studio").build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.empty());
        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.of(staff));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(rolePermissionService.getGrantedPermissions(tenantId, UserRole.OWNER)).thenReturn(List.of("clients.view_all"));

        CurrentUserResponse response = authService.getCurrentUser();

        assertEquals(staffId, response.getId());
        assertEquals("owner@test.com", response.getEmail());
    }

    @Test
    void getCurrentUser_rejectsMissingTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String authUserId = UUID.randomUUID().toString();

        authenticate(staffId, tenantId, authUserId, UserRole.OWNER);

        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .email("owner@test.com")
                .role(UserRole.OWNER)
                .build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.of(staff));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> authService.getCurrentUser());
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void getCurrentUser_rejectsUnauthenticated() {
        ApiException ex = assertThrows(ApiException.class, () -> authService.getCurrentUser());
        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }

    @Test
    void getCurrentUser_usesStaffRoleNotPrincipalRole() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String authUserId = UUID.randomUUID().toString();

        authenticate(staffId, tenantId, authUserId, UserRole.OWNER);

        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .email("artist@test.com")
                .firstName("Studio")
                .lastName("Artist")
                .role(UserRole.ARTIST)
                .build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Ink Studio").build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.of(staff));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(rolePermissionService.getGrantedPermissions(tenantId, UserRole.OWNER))
                .thenReturn(List.of("clients.view_all"));

        CurrentUserResponse response = authService.getCurrentUser();

        assertEquals("artist", response.getRole());
        assertEquals("Studio", response.getFirstName());
        assertEquals(List.of("clients.view_all"), response.getPermissions());
    }

    @Test
    void getCurrentUser_includesLocationIdsFromPrincipal() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        String authUserId = UUID.randomUUID().toString();

        UserPrincipal principal = UserPrincipal.builder()
                .id(staffId)
                .tenantId(tenantId)
                .authUserId(authUserId)
                .role(UserRole.OWNER)
                .email("owner@test.com")
                .locationIds(List.of(locationId))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );

        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .email("owner@test.com")
                .role(UserRole.OWNER)
                .build();
        Tenant tenant = Tenant.builder().id(tenantId).name("Ink Studio").build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.of(staff));
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(rolePermissionService.getGrantedPermissions(tenantId, UserRole.OWNER)).thenReturn(List.of());

        CurrentUserResponse response = authService.getCurrentUser();

        assertEquals(List.of(locationId), response.getLocationIds());
    }

    @Test
    void getCurrentUser_rejectsMissingStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String authUserId = UUID.randomUUID().toString();
        authenticate(staffId, tenantId, authUserId, UserRole.OWNER);

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.empty());
        when(staffRepository.findByIdAndDeletedAtIsNull(staffId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.getCurrentUser());
    }

    private void authenticate(UUID staffId, UUID tenantId, String authUserId, UserRole role) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(staffId)
                .tenantId(tenantId)
                .authUserId(authUserId)
                .role(role)
                .email("owner@test.com")
                .locationIds(List.of())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
