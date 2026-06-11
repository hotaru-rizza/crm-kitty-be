package com.inkflow.crm.module.staff.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.AccountStatus;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.infrastructure.supabase.SupabaseAdminService;
import com.inkflow.crm.module.staff.dto.DeactivateStaffRequest;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffLifecycleService {

    private final StaffLookup staffLookup;
    private final StaffRepository staffRepository;
    private final AppointmentRepository appointmentRepository;
    private final SupabaseAdminService supabaseAdminService;

    @Transactional(readOnly = true)
    public int getFutureAppointmentsCount(UUID staffId) {
        staffLookup.requireStaff(staffId);

        List<Appointment> future = appointmentRepository.findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(
                staffId,
                List.of(AppointmentStatus.NEW, AppointmentStatus.CONFIRMED),
                Instant.now());
        return future.size();
    }

    @Transactional
    public void reactivateStaff(UUID staffId) {
        SecurityUtils.requireAdminAccess();
        Staff staff = staffLookup.requireStaff(staffId);

        if (staff.getAccountStatus() != AccountStatus.DEACTIVATED) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATUS_TRANSITION, "Staff member is not deactivated");
        }

        staff.setAccountStatus(AccountStatus.ACTIVE);
        staffRepository.save(staff);
        log.info("Staff reactivated: staffId={} tenantId={}", staffId, staff.getTenantId());
    }

    @Transactional
    public void deactivateStaff(UUID staffId, DeactivateStaffRequest request) {
        SecurityUtils.requireAdminAccess();
        Staff staff = staffLookup.requireStaff(staffId);

        if (staff.getAccountStatus() == AccountStatus.DEACTIVATED) {
            throw new BusinessRuleException(ErrorCode.STAFF_ALREADY_DEACTIVATED, "Staff member is already deactivated");
        }

        if (request.isCancelFutureAppointments()) {
            cancelFutureAppointments(staffId);
        }

        staff.setAccountStatus(AccountStatus.DEACTIVATED);
        staff.setAvailableForOnlineBooking(false);
        staffRepository.save(staff);

        if (staff.getAuthUserId() != null) {
            supabaseAdminService.revokeAllSessions(staff.getAuthUserId());
        }

        log.info("Staff deactivated: staffId={} tenantId={}", staffId, staff.getTenantId());
    }

    private void cancelFutureAppointments(UUID staffId) {
        List<Appointment> future = appointmentRepository.findByArtistIdAndStatusInAndStartTimeAfterAndDeletedAtIsNull(
                staffId,
                List.of(AppointmentStatus.NEW, AppointmentStatus.CONFIRMED),
                Instant.now());
        future.forEach(appointment -> appointment.setStatus(AppointmentStatus.CANCELLED));
        appointmentRepository.saveAll(future);
    }
}
