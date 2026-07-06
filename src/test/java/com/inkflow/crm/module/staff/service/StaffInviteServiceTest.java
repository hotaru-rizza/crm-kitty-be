package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.StaffInvite;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.LocationRepository;
import com.inkflow.crm.domain.repository.StaffInviteRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.infrastructure.supabase.SupabaseAdminService;
import com.inkflow.crm.infrastructure.supabase.SupabaseAuthUser;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.email.service.sending.StaffInviteNotificationService;
import com.inkflow.crm.module.staff.dto.AcceptInviteRequest;
import com.inkflow.crm.support.AuditMocks;
import com.inkflow.crm.module.staff.dto.InviteInfoDto;
import com.inkflow.crm.module.staff.dto.InviteStaffRequest;
import com.inkflow.crm.module.staff.dto.InviteStaffResultDto;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
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

    @Mock
    private StaffInviteNotificationService staffInviteNotificationService;

    @Mock
    private SupabaseAdminService supabaseAdminService;

    @Mock
    private InkflowProperties inkflowProperties;

    @InjectMocks
    private StaffInviteService staffInviteService;

    @BeforeEach
    void stubAudit() {
        AuditMocks.stubLabelFormatter(auditLabelFormatter);
        InkflowProperties.Invite invite = new InkflowProperties.Invite();
        invite.setTtlDays(7);
        when(inkflowProperties.getInvite()).thenReturn(invite);
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
                .locationIds(List.of(UUID.randomUUID()))
                .build();

        when(staffRepository.existsByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, "exists@test.com"))
                .thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> staffInviteService.inviteStaff(request));
    }

    @Test
    void inviteStaff_resendsPendingInviteForSameEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticate(tenantId, userId);

        StaffInvite pending = StaffInvite.builder()
                .tenantId(tenantId)
                .email("pending@test.com")
                .role(UserRole.ARTIST)
                .token("old-token")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .invitedBy(UUID.randomUUID())
                .locationIds(new java.util.HashSet<>(List.of(UUID.randomUUID())))
                .build();

        InviteStaffRequest request = InviteStaffRequest.builder()
                .email("pending@test.com")
                .role("admin")
                .calendarColor("#6366f1")
                .locationIds(List.of(locationId))
                .build();

        com.inkflow.crm.domain.entity.Location location = com.inkflow.crm.domain.entity.Location.builder()
                .id(locationId)
                .tenantId(tenantId)
                .build();

        when(staffRepository.existsByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, "pending@test.com"))
                .thenReturn(false);
        when(staffInviteRepository.findFirstByTenantIdAndEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(
                tenantId, "pending@test.com"))
                .thenReturn(Optional.of(pending));
        when(locationRepository.findByIdInAndDeletedAtIsNull(any())).thenReturn(List.of(location));
        when(staffInviteRepository.save(any(StaffInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffInviteNotificationService.sendTeamInvite(any(StaffInvite.class))).thenReturn(true);

        InviteStaffResultDto result = staffInviteService.inviteStaff(request);

        assertTrue(result.getToken() != null && !result.getToken().isBlank());
        assertTrue(result.isResent());
        assertTrue(result.isEmailDispatched());
        assertEquals("admin", pending.getRole().getValue());
        assertEquals(userId, pending.getInvitedBy());
        assertNotEquals("old-token", pending.getToken());
        verify(staffInviteNotificationService).sendTeamInvite(pending);
    }

    @Test
    void inviteStaff_rejectsInvalidLocations() {
        UUID tenantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticate(tenantId);

        InviteStaffRequest request = InviteStaffRequest.builder()
                .email("new@test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(List.of(locationId))
                .build();

        when(staffRepository.existsByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, "new@test.com"))
                .thenReturn(false);
        when(staffInviteRepository.findFirstByTenantIdAndEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(
                tenantId, "new@test.com"))
                .thenReturn(Optional.empty());
        when(locationRepository.findByIdInAndDeletedAtIsNull(any())).thenReturn(List.of());

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> staffInviteService.inviteStaff(request));
        assertEquals("Invalid locations for invite", ex.getMessage());
    }

    @Test
    void inviteStaff_createsPendingInviteAndSendsEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        authenticate(tenantId, userId);

        InviteStaffRequest request = InviteStaffRequest.builder()
                .email("New@Test.com")
                .role("artist")
                .calendarColor("#6366f1")
                .locationIds(List.of(locationId))
                .build();

        com.inkflow.crm.domain.entity.Location location = com.inkflow.crm.domain.entity.Location.builder()
                .id(locationId)
                .tenantId(tenantId)
                .build();

        when(staffRepository.existsByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, "new@test.com"))
                .thenReturn(false);
        when(staffInviteRepository.findFirstByTenantIdAndEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(
                tenantId, "new@test.com"))
                .thenReturn(Optional.empty());
        when(locationRepository.findByIdInAndDeletedAtIsNull(any())).thenReturn(List.of(location));
        when(staffInviteRepository.save(any(StaffInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffInviteNotificationService.sendTeamInvite(any(StaffInvite.class))).thenReturn(true);

        InviteStaffResultDto result = staffInviteService.inviteStaff(request);

        assertTrue(result.getToken() != null && !result.getToken().isBlank());
        assertFalse(result.isResent());
        assertTrue(result.isEmailDispatched());

        ArgumentCaptor<StaffInvite> captor = ArgumentCaptor.forClass(StaffInvite.class);
        verify(staffInviteRepository).save(captor.capture());
        StaffInvite saved = captor.getValue();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals("new@test.com", saved.getEmail());
        assertEquals(UserRole.ARTIST, saved.getRole());
        assertEquals(userId, saved.getInvitedBy());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now()));
        verify(staffInviteNotificationService).sendTeamInvite(saved);
    }

    @Test
    void acceptInvite_rejectsExpiredInvite() {
        StaffInvite invite = StaffInvite.builder()
                .email("expired@test.com")
                .role(UserRole.ARTIST)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(staffInviteRepository.findByTokenForUpdate("expired-token")).thenReturn(Optional.of(invite));

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

        when(staffInviteRepository.findByTokenForUpdate("used-token")).thenReturn(Optional.of(invite));

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
    void acceptInvite_rejectsMismatchedSupabaseEmailWhenConfigured() {
        UUID tenantId = UUID.randomUUID();
        StaffInvite invite = StaffInvite.builder()
                .tenantId(tenantId)
                .email("join@test.com")
                .role(UserRole.ARTIST)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .locationIds(new java.util.HashSet<>(List.of(UUID.randomUUID())))
                .build();

        when(staffInviteRepository.findByTokenForUpdate("valid-token")).thenReturn(Optional.of(invite));
        when(staffRepository.existsByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, "join@test.com"))
                .thenReturn(false);
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull("auth-456")).thenReturn(Optional.empty());
        when(supabaseAdminService.isEnabled()).thenReturn(true);
        when(supabaseAdminService.findUserById("auth-456"))
                .thenReturn(Optional.of(new SupabaseAuthUser("auth-456", "other@test.com")));

        AcceptInviteRequest request = AcceptInviteRequest.builder()
                .token("valid-token")
                .firstName("Alex")
                .lastName("Ink")
                .authUserId("auth-456")
                .build();

        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> staffInviteService.acceptInvite(request));
        assertEquals("Auth user email does not match invite", ex.getMessage());
    }

    @Test
    void acceptInvite_resolvesAuthUserByEmailWhenIdLookupFails() {
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

        com.inkflow.crm.domain.entity.Location location = com.inkflow.crm.domain.entity.Location.builder()
                .id(locationId)
                .tenantId(tenantId)
                .build();

        when(staffInviteRepository.findByTokenForUpdate("valid-token")).thenReturn(Optional.of(invite));
        when(staffRepository.existsByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, "join@test.com"))
                .thenReturn(false);
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull("real-auth-id")).thenReturn(Optional.empty());
        when(supabaseAdminService.isEnabled()).thenReturn(true);
        when(supabaseAdminService.findUserById("fake-id")).thenReturn(Optional.empty());
        when(supabaseAdminService.findUserByEmail("join@test.com"))
                .thenReturn(Optional.of(new SupabaseAuthUser("real-auth-id", "join@test.com")));
        when(locationRepository.findByIdInAndDeletedAtIsNull(any())).thenReturn(List.of(location));
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
                .authUserId("fake-id")
                .build();

        staffInviteService.acceptInvite(request);

        ArgumentCaptor<com.inkflow.crm.domain.entity.Staff> staffCaptor =
                ArgumentCaptor.forClass(com.inkflow.crm.domain.entity.Staff.class);
        verify(staffRepository).save(staffCaptor.capture());
        assertEquals("real-auth-id", staffCaptor.getValue().getAuthUserId());
        verify(supabaseAdminService).syncUserTenantClaims("real-auth-id", tenantId, "artist");
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

        com.inkflow.crm.domain.entity.Location location = com.inkflow.crm.domain.entity.Location.builder()
                .id(locationId)
                .tenantId(tenantId)
                .build();

        when(staffInviteRepository.findByTokenForUpdate("valid-token")).thenReturn(Optional.of(invite));
        when(staffRepository.existsByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, "join@test.com"))
                .thenReturn(false);
        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull("auth-456")).thenReturn(Optional.empty());
        when(supabaseAdminService.isEnabled()).thenReturn(false);
        when(locationRepository.findByIdInAndDeletedAtIsNull(any())).thenReturn(List.of(location));
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
        verify(supabaseAdminService).syncUserTenantClaims("auth-456", tenantId, "artist");
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
