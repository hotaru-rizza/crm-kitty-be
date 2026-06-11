package com.inkflow.crm.security;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionAspectTest {

    @Mock
    private RolePermissionService rolePermissionService;

    @InjectMocks
    private PermissionAspect permissionAspect;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBypassPermissionCheckForOwner() throws Exception {
        authenticate(UserRole.OWNER);
        JoinPoint joinPoint = joinPointFor("securedSingle");

        assertDoesNotThrow(() -> permissionAspect.checkPermission(joinPoint));

        verifyNoInteractions(rolePermissionService);
    }

    @Test
    void shouldDenyArtistWithoutRequiredPermission() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(UserRole.ARTIST, tenantId);

        when(rolePermissionService.hasPermission(eq(tenantId), eq(UserRole.ARTIST), any()))
                .thenReturn(false);

        JoinPoint joinPoint = joinPointFor("securedSingle");

        assertThrows(AccessDeniedException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    void shouldAllowArtistWhenAnyRequiredPermissionMatches() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(UserRole.ARTIST, tenantId);

        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.REQUESTS_VIEW.getValue()))
                .thenReturn(true);

        JoinPoint joinPoint = joinPointFor("securedAny");

        assertDoesNotThrow(() -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    void shouldAllowArtistWhenSingleRequiredPermissionMatches() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(UserRole.ARTIST, tenantId);

        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.REQUESTS_CREATE.getValue()))
                .thenReturn(true);

        JoinPoint joinPoint = joinPointFor("securedSingle");

        assertDoesNotThrow(() -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    void shouldDenyArtistWhenRequireAllAndOnlyPartialPermissionsMatch() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(UserRole.ARTIST, tenantId);

        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.REQUESTS_VIEW.getValue()))
                .thenReturn(true);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.REQUESTS_CREATE.getValue()))
                .thenReturn(false);

        JoinPoint joinPoint = joinPointFor("securedAll");

        assertThrows(AccessDeniedException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    void shouldAllowArtistWhenRequireAllAndEveryPermissionMatches() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(UserRole.ARTIST, tenantId);

        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.REQUESTS_VIEW.getValue()))
                .thenReturn(true);
        when(rolePermissionService.hasPermission(tenantId, UserRole.ARTIST, Permission.REQUESTS_CREATE.getValue()))
                .thenReturn(true);

        JoinPoint joinPoint = joinPointFor("securedAll");

        assertDoesNotThrow(() -> permissionAspect.checkPermission(joinPoint));
    }

    @Test
    void shouldCheckPermissionsForAdminRole() throws Exception {
        UUID tenantId = UUID.randomUUID();
        authenticate(UserRole.ADMIN, tenantId);

        when(rolePermissionService.hasPermission(tenantId, UserRole.ADMIN, Permission.REQUESTS_CREATE.getValue()))
                .thenReturn(false);

        JoinPoint joinPoint = joinPointFor("securedSingle");

        assertThrows(AccessDeniedException.class, () -> permissionAspect.checkPermission(joinPoint));
    }

    private JoinPoint joinPointFor(String methodName) throws Exception {
        Method method = DummyController.class.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        return joinPoint;
    }

    private void authenticate(UserRole role) {
        authenticate(role, UUID.randomUUID());
    }

    private void authenticate(UserRole role, UUID tenantId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .role(role)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @SuppressWarnings("unused")
    static class DummyController {
        @RequirePermission(Permission.REQUESTS_CREATE)
        void securedSingle() {
        }

        @RequirePermission({Permission.REQUESTS_VIEW, Permission.REQUESTS_CREATE})
        void securedAny() {
        }

        @RequirePermission(value = {Permission.REQUESTS_VIEW, Permission.REQUESTS_CREATE}, requireAll = true)
        void securedAll() {
        }
    }
}
