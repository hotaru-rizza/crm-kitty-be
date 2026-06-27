package com.inkflow.crm.module.staff.service;

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
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.staff.dto.AcceptInviteRequest;
import com.inkflow.crm.module.staff.dto.InviteInfoDto;
import com.inkflow.crm.module.staff.dto.InviteStaffRequest;
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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffInviteService {

    private static final int INVITE_TTL_DAYS = 7;

    private final StaffRepository staffRepository;
    private final StaffInviteRepository staffInviteRepository;
    private final LocationRepository locationRepository;
    private final StaffMapper staffMapper;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;

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
    public String inviteStaff(InviteStaffRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if (staffRepository.existsByEmailAndTenantIdAndDeletedAtIsNull(request.getEmail(), tenantId)) {
            throw BusinessRuleException.emailAlreadyExists(request.getEmail());
        }
        if (staffInviteRepository.existsByEmailAndTenantIdAndAcceptedAtIsNull(request.getEmail(), tenantId)) {
            throw new BusinessRuleException("Invite already pending for this email");
        }

        String token = UUID.randomUUID().toString();
        StaffInvite invite = StaffInvite.builder()
                .tenantId(tenantId)
                .email(request.getEmail())
                .role(UserRole.fromValue(request.getRole()))
                .calendarColor(request.getCalendarColor())
                .isServiceProvider(request.getIsServiceProvider())
                .token(token)
                .expiresAt(Instant.now().plus(Duration.ofDays(INVITE_TTL_DAYS)))
                .invitedBy(currentUserId)
                .locationIds(new HashSet<>(request.getLocationIds()))
                .build();

        staffInviteRepository.save(invite);
        log.info("Staff invite created: tenantId={} email={}", tenantId, request.getEmail());
        auditRecorder.record(
                AuditAction.STAFF_INVITE,
                AuditEntityType.STAFF,
                token,
                request.getEmail(),
                null,
                request.getRole()
        );
        return token;
    }

    @Transactional
    public StaffDto acceptInvite(AcceptInviteRequest request) {
        StaffInvite invite = requireInvite(request.getToken());
        validateInviteAcceptable(invite);

        Staff staff = buildStaffFromInvite(invite, request);
        staff.setLocations(resolveLocations(invite.getLocationIds()));

        invite.setAcceptedAt(Instant.now());
        staffInviteRepository.save(invite);

        staff = staffRepository.save(staff);
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

    private void validateInviteAcceptable(StaffInvite invite) {
        if (invite.isExpired()) {
            throw new BusinessRuleException("Invite has expired");
        }
        if (invite.isAccepted()) {
            throw new BusinessRuleException("Invite has already been accepted");
        }
    }

    private Staff buildStaffFromInvite(StaffInvite invite, AcceptInviteRequest request) {
        return Staff.builder()
                .tenantId(invite.getTenantId())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(invite.getEmail())
                .phone(request.getPhone())
                .role(invite.getRole())
                .calendarColor(invite.getCalendarColor())
                .isServiceProvider(invite.getIsServiceProvider())
                .authUserId(request.getAuthUserId())
                .status(StaffStatus.WORKING)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    private Set<Location> resolveLocations(Set<UUID> locationIds) {
        return new HashSet<>(locationRepository.findAllById(locationIds));
    }
}
