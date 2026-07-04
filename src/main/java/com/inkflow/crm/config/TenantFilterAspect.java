package com.inkflow.crm.config;

import com.inkflow.crm.security.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class TenantFilterAspect {

    private final EntityManager entityManager;

    @Around("execution(* com.inkflow.crm.module..service..*(..)) "
            + "&& !@within(com.inkflow.crm.config.BypassTenantFilter) "
            + "&& !@annotation(com.inkflow.crm.config.BypassTenantFilter)")
    public Object applyTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        }
        return joinPoint.proceed();
    }
}
