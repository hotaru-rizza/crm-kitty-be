package com.inkflow.crm.security;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    private final RolePermissionService rolePermissionService;

    @Before("@annotation(com.inkflow.crm.security.RequirePermission)")
    public void checkPermission(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        if (annotation == null) return;

        Permission[] requiredPermissions = annotation.value();
        boolean requireAll = annotation.requireAll();

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UserRole role = SecurityUtils.getCurrentUserRole();

        if (role == UserRole.OWNER) return;

        boolean hasAccess;
        if (requireAll) {
            hasAccess = Arrays.stream(requiredPermissions)
                    .allMatch(permission -> rolePermissionService.hasPermission(tenantId, role, permission.getValue()));
        } else {
            hasAccess = Arrays.stream(requiredPermissions)
                    .anyMatch(permission -> rolePermissionService.hasPermission(tenantId, role, permission.getValue()));
        }

        if (!hasAccess) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }
}
