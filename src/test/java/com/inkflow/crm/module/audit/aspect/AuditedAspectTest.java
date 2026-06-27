package com.inkflow.crm.module.audit.aspect;

import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.module.audit.annotation.Audited;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditExpressionEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanFactory;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditedAspectTest {

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditExpressionEvaluator expressionEvaluator;

    @Mock
    private BeanFactory beanFactory;

    @InjectMocks
    private AuditedAspect auditedAspect;

    @Test
    void afterReturning_recordsAuditFromAnnotation() throws Exception {
        UUID staffId = UUID.randomUUID();
        var joinPoint = org.mockito.Mockito.mock(org.aspectj.lang.JoinPoint.class);
        var signature = org.mockito.Mockito.mock(org.aspectj.lang.reflect.MethodSignature.class);
        var method = SampleService.class.getMethod("updateSample", UUID.class);

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(expressionEvaluator.evalString(eq("#staffId.toString()"), eq(joinPoint), any(), eq(beanFactory)))
                .thenReturn(staffId.toString());
        when(expressionEvaluator.evalString(eq("@auditLabelFormatter.staff(@staffLookup.requireStaff(#staffId))"),
                eq(joinPoint), any(), eq(beanFactory)))
                .thenReturn("Jane Doe");
        when(expressionEvaluator.evalString(eq("'FAQ оновлено'"), eq(joinPoint), any(), eq(beanFactory)))
                .thenReturn("FAQ оновлено");
        when(expressionEvaluator.evalUuid(eq(""), eq(joinPoint), any(), eq(beanFactory)))
                .thenReturn(null);

        auditedAspect.afterReturning(joinPoint, List.of());

        verify(auditRecorder).record(
                AuditAction.UPDATE,
                AuditEntityType.STAFF,
                staffId.toString(),
                "Jane Doe",
                null,
                "FAQ оновлено"
        );
    }

    static class SampleService {
        @Audited(
                action = AuditAction.UPDATE,
                entityType = AuditEntityType.STAFF,
                entityId = "#staffId.toString()",
                entityLabel = "@auditLabelFormatter.staff(@staffLookup.requireStaff(#staffId))",
                details = "'FAQ оновлено'"
        )
        public List<String> updateSample(UUID staffId) {
            return List.of();
        }
    }
}
