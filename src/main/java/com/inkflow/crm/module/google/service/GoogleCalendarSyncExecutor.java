package com.inkflow.crm.module.google.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class GoogleCalendarSyncExecutor {

    private final AppointmentRepository appointmentRepository;
    private final StaffRepository staffRepository;
    private final InkflowProperties inkflowProperties;
    private final GoogleCalendarEventBuilder eventBuilder;
    private final GoogleCalendarClientFactory calendarClientFactory;

    void syncNewAppointment(UUID tenantId, UUID appointmentId) {
        runWithTenant(tenantId, () -> doSyncNewAppointment(appointmentId));
    }

    void syncUpdatedAppointment(UUID tenantId, UUID appointmentId) {
        runWithTenant(tenantId, () -> doSyncUpdatedAppointment(appointmentId));
    }

    void syncDeletedAppointment(UUID tenantId, UUID appointmentId, String googleEventId, UUID artistId) {
        runWithTenant(tenantId, () -> doSyncDeletedAppointment(appointmentId, googleEventId, artistId));
    }

    private void runWithTenant(UUID tenantId, Runnable action) {
        TenantContext.setCurrentTenant(tenantId);
        try {
            action.run();
        } finally {
            TenantContext.clear();
        }
    }

    @Transactional
    void doSyncNewAppointment(UUID appointmentId) {
        Appointment appointment = loadAppointment(appointmentId);
        if (appointment == null) {
            return;
        }

        Staff artist = appointment.getArtist();
        if (!artist.isGoogleCalendarConnected()) {
            return;
        }

        try {
            Calendar calendarService = calendarClientFactory.getCalendarService(artist);
            Event event = eventBuilder.buildEvent(appointment, inkflowProperties.getDefaultTimezone());
            Event created = calendarService.events()
                    .insert(artist.getGoogleCalendarId(), event)
                    .execute();

            appointment.setGoogleEventId(created.getId());
            appointmentRepository.save(appointment);
            log.info("Google event created: {} for appointment {}", created.getId(), appointmentId);
        } catch (Exception exception) {
            log.warn("Failed to sync new appointment {} to Google Calendar (tenantId={}): {}",
                    appointmentId, appointment.getTenantId(), exception.getMessage());
        }
    }

    @Transactional
    void doSyncUpdatedAppointment(UUID appointmentId) {
        Appointment appointment = loadAppointment(appointmentId);
        if (appointment == null || appointment.getGoogleEventId() == null) {
            return;
        }

        Staff artist = appointment.getArtist();
        if (!artist.isGoogleCalendarConnected()) {
            return;
        }

        try {
            Calendar calendarService = calendarClientFactory.getCalendarService(artist);
            Event event = eventBuilder.buildEvent(appointment, inkflowProperties.getDefaultTimezone());
            calendarService.events()
                    .update(artist.getGoogleCalendarId(), appointment.getGoogleEventId(), event)
                    .execute();

            log.info("Google event updated: {} for appointment {}",
                    appointment.getGoogleEventId(), appointmentId);
        } catch (Exception exception) {
            log.warn("Failed to sync updated appointment {} to Google Calendar (tenantId={}): {}",
                    appointmentId, appointment.getTenantId(), exception.getMessage());
        }
    }

    @Transactional
    void doSyncDeletedAppointment(UUID appointmentId, String googleEventId, UUID artistId) {
        if (googleEventId == null) {
            return;
        }

        Staff artist = staffRepository.findByIdAndDeletedAtIsNull(artistId).orElse(null);
        if (artist == null || !artist.isGoogleCalendarConnected()) {
            return;
        }

        try {
            Calendar calendarService = calendarClientFactory.getCalendarService(artist);
            calendarService.events()
                    .delete(artist.getGoogleCalendarId(), googleEventId)
                    .execute();

            log.info("Google event deleted: {} for appointment {}", googleEventId, appointmentId);
        } catch (Exception exception) {
            log.warn("Failed to delete Google event for appointment {} (tenantId={}): {}",
                    appointmentId, artist.getTenantId(), exception.getMessage());
        }
    }

    private Appointment loadAppointment(UUID appointmentId) {
        return appointmentRepository.findByIdAndDeletedAtIsNull(appointmentId).orElse(null);
    }
}
