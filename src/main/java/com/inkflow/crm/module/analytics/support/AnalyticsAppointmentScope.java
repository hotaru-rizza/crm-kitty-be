package com.inkflow.crm.module.analytics.support;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.enums.Permission;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.settings.service.RolePermissionService;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Loads appointments for analytics, scoped to the current user when they lack VIEW_ALL.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsAppointmentScope {

    private final AppointmentRepository appointmentRepository;
    private final RolePermissionService rolePermissionService;

    public List<Appointment> findForCalendarAnalytics(Instant from, Instant to, UUID locationId) {
        return findScoped(from, to, locationId, Permission.CALENDAR_VIEW_ALL);
    }

    public List<Appointment> findForClientAnalytics(Instant from, Instant to, UUID locationId) {
        return findScoped(from, to, locationId, Permission.CLIENTS_VIEW_ALL);
    }

    private List<Appointment> findScoped(
            Instant from,
            Instant to,
            UUID locationId,
            Permission viewAllPermission) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        if (rolePermissionService.hasPermission(
                tenantId,
                SecurityUtils.getCurrentUserRole(),
                viewAllPermission.getValue())) {
            return appointmentRepository.findByDateRange(from, to, locationId);
        }

        UUID artistId = SecurityUtils.getCurrentUserId();
        List<Appointment> ownAppointments = appointmentRepository.findByArtistIdAndDateRange(artistId, from, to);

        if (locationId == null) {
            return ownAppointments;
        }

        return ownAppointments.stream()
                .filter(appointment -> appointment.getLocation() != null
                        && locationId.equals(appointment.getLocation().getId()))
                .toList();
    }
}
