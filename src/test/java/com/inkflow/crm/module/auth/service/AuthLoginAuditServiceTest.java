package com.inkflow.crm.module.auth.service;

import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.AuditLogRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthLoginAuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private AuditRecorder auditRecorder;

    @InjectMocks
    private AuthLoginAuditService authLoginAuditService;

    @Test
    void recordLoginIfNew_writesAuditWhenSessionNotSeenBefore() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String sessionId = "session-abc";

        UserPrincipal principal = UserPrincipal.builder()
                .id(staffId)
                .tenantId(tenantId)
                .sessionId(sessionId)
                .role(UserRole.OWNER)
                .email("owner@test.com")
                .build();
        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .firstName("Owner")
                .lastName("User")
                .build();

        when(auditLogRepository.existsByActorIdAndActionAndDetails(staffId,
                AuditAction.LOGIN.getValue(),
                AuthLoginAuditService.SESSION_DETAILS_PREFIX + sessionId
        )).thenReturn(false);

        authLoginAuditService.recordLoginIfNew(principal, staff);

        verify(auditRecorder).record(
                AuditAction.LOGIN,
                AuditEntityType.STAFF,
                staffId.toString(),
                staff.getFullName(),
                null,
                AuthLoginAuditService.SESSION_DETAILS_PREFIX + sessionId
        );
    }

    @Test
    void recordLoginIfNew_skipsWhenSessionAlreadyLogged() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        String sessionId = "session-abc";

        UserPrincipal principal = UserPrincipal.builder()
                .id(staffId)
                .tenantId(tenantId)
                .sessionId(sessionId)
                .role(UserRole.OWNER)
                .email("owner@test.com")
                .build();
        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .firstName("Owner")
                .lastName("User")
                .build();

        when(auditLogRepository.existsByActorIdAndActionAndDetails(
                eq(staffId),
                eq(AuditAction.LOGIN.getValue()),
                eq(AuthLoginAuditService.SESSION_DETAILS_PREFIX + sessionId)
        )).thenReturn(true);

        authLoginAuditService.recordLoginIfNew(principal, staff);

        verify(auditRecorder, never()).record(
                eq(AuditAction.LOGIN),
                eq(AuditEntityType.STAFF),
                eq(staffId.toString()),
                eq(staff.getFullName()),
                eq(null),
                eq(AuthLoginAuditService.SESSION_DETAILS_PREFIX + sessionId)
        );
    }

    @Test
    void recordLoginIfNew_skipsWhenSessionIdMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        UserPrincipal principal = UserPrincipal.builder()
                .id(staffId)
                .tenantId(tenantId)
                .role(UserRole.OWNER)
                .email("owner@test.com")
                .build();
        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .firstName("Owner")
                .lastName("User")
                .build();

        authLoginAuditService.recordLoginIfNew(principal, staff);

        verify(auditRecorder, never()).record(
                eq(AuditAction.LOGIN),
                eq(AuditEntityType.STAFF),
                eq(staffId.toString()),
                eq(staff.getFullName()),
                eq(null),
                eq(AuthLoginAuditService.SESSION_DETAILS_PREFIX + "session-abc")
        );
    }
}
