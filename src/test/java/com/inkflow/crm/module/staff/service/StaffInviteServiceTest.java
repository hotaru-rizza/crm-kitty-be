package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.StaffInvite;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffInviteRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.staff.dto.AcceptInviteRequest;
import com.inkflow.crm.support.AuditMocks;
import com.inkflow.crm.module.staff.dto.InviteInfoDto;
import com.inkflow.crm.module.staff.dto.InviteStaffRequest;
import com.inkflow.crm.module.staff.dto.StaffDto;
import com.inkflow.crm.module.staff.mapper.StaffMapper;
import com.inkflow.crm.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffInviteServiceTest {

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private StaffInviteRepository staffInviteRepository;

    @Mock
    private LocationRepository locationRepository;

    @Mock
    private StaffMapper staffMapper;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @InjectMocks
    private StaffInviteService staffInviteService;

    @BeforeEach
    void stubAudit() {
        AuditMocks.stubLabelFormatter(auditLabelFormatter);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getInviteInfo_returnsExpiredFlag() {
        StaffInvite invite = StaffInvite.builder()
                .email("artist@test.com")
                .role(UserRole.ARTIST)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(staffInviteRepository.findByToken("expired-token")).thenReturn(Optional.of(invite));

        InviteInfoDto info = staffInviteService.getInviteInfo("expired-token");

        assertEquals("artist@test.com", info.getEmail());
        assertTrue(info.isExpired());
    }

    @Test
    void getInviteInfo_rejectsInvalidToken() {
        when(staffInviteRepository.findByToken("missing-token")).thenReturn(Optional.empty());

        assertThrows(BusinessRuleException.class, () -> staffInviteService.getInviteInfo("missing-token"));
    }

    @Test
    void inviteStaff_rejectsDuplicateEmail() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        InviteStaffRequest request = InviteStaffRequest.builder()
                .email("exists@test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .build();

        when(staffRepository.existsByEmailAndDeletedAtIsNull("exists@test.com"))
                .thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> staffInviteService.inviteStaff(request));
    }

    @Test
    void inviteStaff_rejectsPendingInviteForSameEmail() {
        UUID tenantId = UUID.randomUUID();
        authenticate(tenantId);

        InviteStaffRequest request = InviteStaffRequest.builder()
                .email("pending@test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(List.of(UUID.randomUUID()))
                .build();

        when(staffRepository.existsByEmailAndDeletedAtIsNull("pending@test.com")).thenReturn(false);
        when(staffInviteRepository.existsByEmailAndAcceptedAtIsNull("pending@test.com"))
                .thenReturn(true);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> staffInviteService.inviteStaff(request));
        assertEquals("Invite already pending for this email", ex.getMessage());
    }

    @Test
    void inviteStaff_createsPendingInvite() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        authenticate(tenantId, userId);

        InviteStaffRequest request = InviteStaffRequest.builder()
                .email("new@test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(java.util.List.of(UUID.randomUUID()))
                .build();

        when(staffRepository.existsByEmailAndDeletedAtIsNull("new@test.com")).thenReturn(false);
        when(staffInviteRepository.existsByEmailAndAcceptedAtIsNull("new@test.com")).thenReturn(false);
        when(staffInviteRepository.save(any(StaffInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String token = staffInviteService.inviteStaff(request);

        assertTrue(token != null && !token.isBlank());

        ArgumentCaptor<StaffInvite> captor = ArgumentCaptor.forClass(StaffInvite.class);
        verify(staffInviteRepository).save(captor.capture());
        StaffInvite saved = captor.getValue();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals("new@test.com", saved.getEmail());
        assertEquals(UserRole.ARTIST, saved.getRole());
        assertEquals(userId, saved.getInvitedBy());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void acceptInvite_rejectsExpiredInvite() {
        StaffInvite invite = StaffInvite.builder()
                .email("expired@test.com")
                .role(UserRole.ARTIST)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(staffInviteRepository.findByToken("expired-token")).thenReturn(Optional.of(invite));

        AcceptInviteRequest request = AcceptInviteRequest.builder()
                .token("expired-token")
                .firstName("Alex")
                .lastName("Ink")
                .authUserId("auth-123")
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> staffInviteService.acceptInvite(request));
        assertEquals("Invite has expired", ex.getMessage());
    }

    @Test
    void acceptInvite_rejectsAlreadyAcceptedInvite() {
        StaffInvite invite = StaffInvite.builder()
                .email("accepted@test.com")
                .role(UserRole.ARTIST)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .acceptedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(staffInviteRepository.findByToken("used-token")).thenReturn(Optional.of(invite));

        AcceptInviteRequest request = AcceptInviteRequest.builder()
                .token("used-token")
                .firstName("Alex")
                .lastName("Ink")
                .authUserId("auth-123")
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> staffInviteService.acceptInvite(request));
        assertEquals("Invite has already been accepted", ex.getMessage());
    }

    @Test
    void acceptInvite_marksInviteAcceptedAndCreatesStaff() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        StaffInvite invite = StaffInvite.builder()
                .tenantId(tenantId)
                .email("join@test.com")
                .role(UserRole.ARTIST)
                .calendarColor("#6366f1")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .locationIds(new java.util.HashSet<>(List.of(locationId)))
                .build();

        when(staffInviteRepository.findByToken("valid-token")).thenReturn(Optional.of(invite));
        when(locationRepository.findAllById(invite.getLocationIds())).thenReturn(List.of());
        when(staffInviteRepository.save(any(StaffInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffRepository.save(any(com.inkflow.crm.domain.entity.Staff.class)))
                .thenAnswer(invocation -> {
                    com.inkflow.crm.domain.entity.Staff staff = invocation.getArgument(0);
                    staff.setId(UUID.randomUUID());
                    return staff;
                });
        when(staffMapper.toDto(any(com.inkflow.crm.domain.entity.Staff.class)))
                .thenReturn(StaffDto.builder().email("join@test.com").build());

        AcceptInviteRequest request = AcceptInviteRequest.builder()
                .token("valid-token")
                .firstName("Alex")
                .lastName("Ink")
                .authUserId("auth-456")
                .build();

        StaffDto result = staffInviteService.acceptInvite(request);

        assertEquals("join@test.com", result.getEmail());

        ArgumentCaptor<StaffInvite> inviteCaptor = ArgumentCaptor.forClass(StaffInvite.class);
        verify(staffInviteRepository).save(inviteCaptor.capture());
        assertNotNull(inviteCaptor.getValue().getAcceptedAt());

        ArgumentCaptor<com.inkflow.crm.domain.entity.Staff> staffCaptor =
                ArgumentCaptor.forClass(com.inkflow.crm.domain.entity.Staff.class);
        verify(staffRepository).save(staffCaptor.capture());
        com.inkflow.crm.domain.entity.Staff savedStaff = staffCaptor.getValue();
        assertEquals("Alex", savedStaff.getFirstName());
        assertEquals("join@test.com", savedStaff.getEmail());
        assertEquals("auth-456", savedStaff.getAuthUserId());
        assertEquals(tenantId, savedStaff.getTenantId());
    }

    private void authenticate(UUID tenantId) {
        authenticate(tenantId, UUID.randomUUID());
    }

    private void authenticate(UUID tenantId, UUID userId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .tenantId(tenantId)
                .role(com.inkflow.crm.domain.enums.UserRole.OWNER)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
