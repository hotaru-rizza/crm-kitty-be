package com.inkflow.crm.module.audit.aspect;

import com.inkflow.crm.module.audit.annotation.Audited;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditExpressionEvaluator;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditedAspect {

    private final AuditRecorder auditRecorder;
    private final AuditExpressionEvaluator expressionEvaluator;
    private final BeanFactory beanFactory;

    @AfterReturning(pointcut = "@annotation(com.inkflow.crm.module.audit.annotation.Audited)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Audited audited = method.getAnnotation(Audited.class);
        if (audited == null) {
            return;
        }

        String entityId = expressionEvaluator.evalString(audited.entityId(), joinPoint, result, beanFactory);
        if (entityId == null || entityId.isBlank()) {
            return;
        }

        String entityLabel = expressionEvaluator.evalString(audited.entityLabel(), joinPoint, result, beanFactory);
        UUID subjectClientId = expressionEvaluator.evalUuid(audited.subjectClientId(), joinPoint, result, beanFactory);
        String details = expressionEvaluator.evalString(audited.details(), joinPoint, result, beanFactory);

        auditRecorder.record(
                audited.action(),
                audited.entityType(),
                entityId,
                entityLabel != null ? entityLabel : entityId,
                subjectClientId,
                details
        );
    }
}
