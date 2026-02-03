package com.inkflow.crm.security;

import com.inkflow.crm.common.exception.AccessDeniedException;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.settings.service.SettingsService;
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

    private final SettingsService settingsService;

    @Before("@annotation(com.inkflow.crm.security.RequirePermission)")
    public void checkPermission(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequirePermission annotation = method.getAnnotation(RequirePermission.class);

        if (annotation == null) return;

        String[] requiredPermissions = annotation.value();
        boolean requireAll = annotation.requireAll();

        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UserRole role = SecurityUtils.getCurrentUserRole();

        if (role == UserRole.OWNER) return;

        boolean hasAccess;
        if (requireAll) {
            hasAccess = Arrays.stream(requiredPermissions)
                    .allMatch(p -> settingsService.hasPermission(tenantId, role, p));
        } else {
            hasAccess = Arrays.stream(requiredPermissions)
                    .anyMatch(p -> settingsService.hasPermission(tenantId, role, p));
        }

        if (!hasAccess) {
            throw AccessDeniedException.insufficientPermissions();
        }
    }
}
