package com.inkflow.crm.module.auth.service;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.repository.AuditLogRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthLoginAuditService {

    static final String SESSION_DETAILS_PREFIX = "session:";

    private final AuditLogRepository auditLogRepository;
    private final AuditRecorder auditRecorder;

    public void recordLoginIfNew(UserPrincipal principal, Staff staff) {
        if (principal.getTenantId() == null || !StringUtils.hasText(principal.getSessionId())) {
            return;
        }

        String sessionDetails = SESSION_DETAILS_PREFIX + principal.getSessionId();
        if (auditLogRepository.existsByTenantIdAndActorIdAndActionAndDetails(
                principal.getTenantId(),
                staff.getId(),
                AuditAction.LOGIN.getValue(),
                sessionDetails
        )) {
            return;
        }

        auditRecorder.record(
                AuditAction.LOGIN,
                AuditEntityType.STAFF,
                staff.getId().toString(),
                staff.getFullName(),
                null,
                sessionDetails
        );
    }
}
