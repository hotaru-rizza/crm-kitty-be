package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.config.BypassTenantFilter;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.entity.StaffInvite;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.enums.StaffStatus;
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
import com.inkflow.crm.module.staff.dto.InviteInfoDto;
import com.inkflow.crm.module.staff.dto.InviteStaffRequest;
import com.inkflow.crm.module.staff.dto.InviteStaffResultDto;
import com.inkflow.crm.module.staff.dto.PendingStaffInviteDto;
import com.inkflow.crm.module.staff.dto.StaffDto;
import com.inkflow.crm.module.staff.mapper.StaffMapper;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffInviteService {

    private final StaffRepository staffRepository;
    private final StaffInviteRepository staffInviteRepository;
    private final LocationRepository locationRepository;
    private final StaffMapper staffMapper;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;
    private final StaffInviteNotificationService staffInviteNotificationService;
    private final SupabaseAdminService supabaseAdminService;
    private final InkflowProperties inkflowProperties;

    @BypassTenantFilter
    @Transactional(readOnly = true)
    public InviteInfoDto getInviteInfo(String token) {
        StaffInvite invite = requireInvite(token);

        return InviteInfoDto.builder()
                .email(invite.getEmail())
                .role(invite.getRole().getValue())
                .expiresAt(invite.getExpiresAt())
                .expired(invite.isExpired())
                .accepted(invite.isAccepted())
                .build();
    }

    @Transactional
    public InviteStaffResultDto inviteStaff(InviteStaffRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        String normalizedEmail = normalizeEmail(request.getEmail());

        validateNoExistingStaff(tenantId, normalizedEmail);
        resolveLocations(tenantId, new HashSet<>(request.getLocationIds()));

        Optional<StaffInvite> pendingInvite = staffInviteRepository
                .findFirstByTenantIdAndEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(tenantId, normalizedEmail);

        boolean resent = pendingInvite.isPresent();
        StaffInvite invite = pendingInvite
                .map(existing -> refreshPendingInvite(existing, request, currentUserId))
                .orElseGet(() -> buildNewInvite(tenantId, normalizedEmail, request, currentUserId));

        invite = staffInviteRepository.save(invite);
        if (resent) {
            log.info("Staff invite resent: tenantId={} email={}", tenantId, normalizedEmail);
        } else {
            log.info("Staff invite created: tenantId={} email={}", tenantId, normalizedEmail);
        }

        auditRecorder.record(
                AuditAction.STAFF_INVITE,
                AuditEntityType.STAFF,
                invite.getToken(),
                normalizedEmail,
                null,
                request.getRole()
        );

        boolean emailDispatched = staffInviteNotificationService.sendTeamInvite(invite);
        return InviteStaffResultDto.builder()
                .token(invite.getToken())
                .resent(resent)
                .emailDispatched(emailDispatched)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PendingStaffInviteDto> listPendingInvites() {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return staffInviteRepository.findByTenantIdAndAcceptedAtIsNullOrderByCreatedAtDesc(tenantId)
                .stream()
                .map(this::toPendingDto)
                .toList();
    }

    @Transactional
    public void revokePendingInvite(UUID inviteId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        StaffInvite invite = staffInviteRepository.findById(inviteId)
                .orElseThrow(() -> new BusinessRuleException("Invite not found"));

        if (!tenantId.equals(invite.getTenantId())) {
            throw new BusinessRuleException("Invite not found");
        }
        if (invite.isAccepted()) {
            throw new BusinessRuleException("Invite has already been accepted");
        }

        staffInviteRepository.delete(invite);
        log.info("Staff invite revoked: tenantId={} inviteId={} email={}", tenantId, inviteId, invite.getEmail());
    }

    @BypassTenantFilter
    @Transactional
    public StaffDto acceptInvite(AcceptInviteRequest request) {
        StaffInvite invite = staffInviteRepository.findByTokenForUpdate(request.getToken())
                .orElseThrow(() -> new BusinessRuleException("Invalid invite token"));
        validateInviteAcceptable(invite);
        String authUserId = resolveAuthUserId(invite, request);

        Staff staff = buildStaffFromInvite(invite, request, authUserId);
        staff.setLocations(resolveLocations(invite.getTenantId(), invite.getLocationIds()));

        invite.setAcceptedAt(Instant.now());
        staffInviteRepository.save(invite);

        staff = staffRepository.save(staff);
        syncSupabaseClaims(staff);

        log.info("Staff invite accepted: tenantId={} staffId={}", invite.getTenantId(), staff.getId());
        auditRecorder.recordSystem(
                invite.getTenantId(),
                AuditAction.CREATE,
                AuditEntityType.STAFF,
                staff.getId().toString(),
                auditLabelFormatter.staff(staff),
                null,
                "Прийнято запрошення"
        );
        return staffMapper.toDto(staff);
    }

    private StaffInvite requireInvite(String token) {
        return staffInviteRepository.findByToken(token)
                .orElseThrow(() -> new BusinessRuleException("Invalid invite token"));
    }

    private void validateNoExistingStaff(UUID tenantId, String email) {
        if (staffRepository.existsByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(tenantId, email)) {
            throw BusinessRuleException.emailAlreadyExists(email);
        }
    }

    private StaffInvite buildNewInvite(
            UUID tenantId,
            String normalizedEmail,
            InviteStaffRequest request,
            UUID currentUserId) {
        return StaffInvite.builder()
                .tenantId(tenantId)
                .email(normalizedEmail)
                .role(UserRole.fromValue(request.getRole()))
                .calendarColor(request.getCalendarColor())
                .isServiceProvider(request.getIsServiceProvider())
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plus(Duration.ofDays(inviteTtlDays())))
                .invitedBy(currentUserId)
                .locationIds(new HashSet<>(request.getLocationIds()))
                .build();
    }

    private StaffInvite refreshPendingInvite(
            StaffInvite invite,
            InviteStaffRequest request,
            UUID currentUserId) {
        invite.setRole(UserRole.fromValue(request.getRole()));
        invite.setCalendarColor(request.getCalendarColor());
        invite.setIsServiceProvider(request.getIsServiceProvider());
        invite.setLocationIds(new HashSet<>(request.getLocationIds()));
        invite.setInvitedBy(currentUserId);
        invite.setToken(UUID.randomUUID().toString());
        invite.setExpiresAt(Instant.now().plus(Duration.ofDays(inviteTtlDays())));
        return invite;
    }

    private void validateInviteAcceptable(StaffInvite invite) {
        if (invite.isExpired()) {
            throw new BusinessRuleException("Invite has expired");
        }
        if (invite.isAccepted()) {
            throw new BusinessRuleException("Invite has already been accepted");
        }
    }

    private String resolveAuthUserId(StaffInvite invite, AcceptInviteRequest request) {
        if (staffRepository.existsByTenantIdAndEmailIgnoreCaseAndDeletedAtIsNull(invite.getTenantId(), invite.getEmail())) {
            throw BusinessRuleException.emailAlreadyExists(invite.getEmail());
        }

        if (!supabaseAdminService.isEnabled()) {
            if (staffRepository.findByAuthUserIdAndDeletedAtIsNull(request.getAuthUserId()).isPresent()) {
                throw new BusinessRuleException("Auth user already linked to a staff account");
            }
            return request.getAuthUserId();
        }

        SupabaseAuthUser authUser = supabaseAdminService.findUserById(request.getAuthUserId())
                .or(() -> supabaseAdminService.findUserByEmail(invite.getEmail()))
                .orElseThrow(() -> new BusinessRuleException("Invalid auth user"));

        if (!emailsMatch(authUser.email(), invite.getEmail())) {
            throw new BusinessRuleException("Auth user email does not match invite");
        }
        if (staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUser.id()).isPresent()) {
            throw new BusinessRuleException("Auth user already linked to a staff account");
        }
        return authUser.id();
    }

    private Staff buildStaffFromInvite(StaffInvite invite, AcceptInviteRequest request, String authUserId) {
        return Staff.builder()
                .tenantId(invite.getTenantId())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(invite.getEmail())
                .phone(request.getPhone())
                .role(invite.getRole())
                .calendarColor(invite.getCalendarColor())
                .isServiceProvider(invite.getIsServiceProvider())
                .authUserId(authUserId)
                .status(StaffStatus.WORKING)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    private Set<Location> resolveLocations(UUID tenantId, Set<UUID> locationIds) {
        List<Location> locations = locationRepository.findByIdInAndDeletedAtIsNull(locationIds);
        boolean allBelongToTenant = locations.size() == locationIds.size()
                && locations.stream().allMatch(location -> tenantId.equals(location.getTenantId()));
        if (!allBelongToTenant) {
            throw new BusinessRuleException("Invalid locations for invite");
        }
        return new HashSet<>(locations);
    }

    private void syncSupabaseClaims(Staff staff) {
        if (staff.getAuthUserId() == null) {
            return;
        }
        supabaseAdminService.syncUserTenantClaims(
                staff.getAuthUserId(),
                staff.getTenantId(),
                staff.getRole().getValue()
        );
    }

    private int inviteTtlDays() {
        return Math.max(1, inkflowProperties.getInvite().getTtlDays());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean emailsMatch(String left, String right) {
        return normalizeEmail(left).equals(normalizeEmail(right));
    }

    private PendingStaffInviteDto toPendingDto(StaffInvite invite) {
        return PendingStaffInviteDto.builder()
                .id(invite.getId())
                .email(invite.getEmail())
                .role(invite.getRole().getValue())
                .expiresAt(invite.getExpiresAt())
                .createdAt(invite.getCreatedAt())
                .expired(invite.isExpired())
                .build();
    }
}
