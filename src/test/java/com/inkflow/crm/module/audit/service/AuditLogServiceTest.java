package com.inkflow.crm.module.audit.service;

import com.inkflow.crm.domain.entity.AuditLogEntry;
import com.inkflow.crm.domain.repository.AuditLogRepository;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.module.audit.dto.AuditLogDto;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository repo;

    @InjectMocks
    private AuditLogService auditLogService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldPersistAuditEntryWhenLoggingExplicitActor() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(repo.save(any(AuditLogEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditLogService.log(tenantId, actorId, "owner@test.com", "CREATE", "Client", "client-1", "John Doe", null);

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repo).save(captor.capture());

        AuditLogEntry saved = captor.getValue();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(actorId, saved.getActorId());
        assertEquals("owner@test.com", saved.getActorName());
        assertEquals("CREATE", saved.getAction());
        assertEquals("Client", saved.getEntityType());
        assertEquals("client-1", saved.getEntityId());
        assertEquals("John Doe", saved.getEntityLabel());
    }

    @Test
    void shouldPersistDetailsWhenProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(repo.save(any(AuditLogEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditLogService.log(tenantId, actorId, "owner@test.com", "UPDATE", "Client", "client-1", "John Doe", "phone changed");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repo).save(captor.capture());
        assertEquals("phone changed", captor.getValue().getDetails());
    }

    @Test
    void shouldSwallowRepositoryExceptionWhenLogging() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        doThrow(new RuntimeException("db down")).when(repo).save(any(AuditLogEntry.class));

        assertDoesNotThrow(() -> auditLogService.log(
                tenantId, actorId, "owner@test.com", "DELETE", "Client", "client-1", "John Doe", null
        ));
    }

    @Test
    void shouldSkipLoggingWhenNoAuthenticatedUser() {
        auditLogService.logCurrent("CREATE", "Client", "client-1", "John Doe");

        verify(repo, never()).save(any());
    }

    @Test
    void shouldLogCurrentWithAuthenticatedUser() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        authenticate(tenantId, actorId, "owner@test.com");

        when(repo.save(any(AuditLogEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auditLogService.logCurrent("UPDATE", "Appointment", "appt-1", "Session #1");

        ArgumentCaptor<AuditLogEntry> captor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(repo).save(captor.capture());

        AuditLogEntry saved = captor.getValue();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(actorId, saved.getActorId());
        assertEquals("owner@test.com", saved.getActorName());
        assertEquals("UPDATE", saved.getAction());
        assertEquals("Appointment", saved.getEntityType());
    }

    @Test
    void shouldSwallowExceptionWhenLogCurrentFails() {
        authenticate(UUID.randomUUID(), UUID.randomUUID(), "owner@test.com");
        doThrow(new RuntimeException("db down")).when(repo).save(any(AuditLogEntry.class));

        assertDoesNotThrow(() -> auditLogService.logCurrent("DELETE", "Client", "client-1", "John Doe"));
    }

    @Test
    void shouldReturnPagedAuditLogsForCurrentTenant() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId, UUID.randomUUID(), "owner@test.com");

        UUID entryId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-01T10:00:00Z");
        AuditLogEntry entry = AuditLogEntry.builder()
                .id(entryId)
                .tenantId(tenantId)
                .actorId(UUID.randomUUID())
                .actorName("owner@test.com")
                .action("UPDATE")
                .entityType("Client")
                .entityId("client-1")
                .entityLabel("John Doe")
                .details("email updated")
                .createdAt(createdAt)
                .build();

        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        Page<AuditLogDto> result = auditLogService.getLog(null, null, null, null, 0, 20);

        assertEquals(1, result.getTotalElements());
        AuditLogDto dto = result.getContent().getFirst();
        assertEquals(entryId, dto.getId());
        assertEquals("UPDATE", dto.getAction());
        assertEquals("Client", dto.getEntityType());
        assertEquals("email updated", dto.getDetails());
        assertEquals(createdAt, dto.getCreatedAt());
        verify(repo).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldQueryAuditLogsWithFilters() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        authenticate(tenantId, actorId, "owner@test.com");

        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T23:59:59Z");

        when(repo.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        auditLogService.getLog(actorId, "Client", from, to, 1, 10);

        verify(repo).findAll(any(Specification.class), any(Pageable.class));
    }

    private void authenticate(UUID tenantId, UUID userId, String email) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .email(email)
                .role(UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
