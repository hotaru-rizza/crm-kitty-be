package com.inkflow.crm.module.google.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.calendar.Calendar;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.config.BypassTenantFilter;
import com.inkflow.crm.config.GoogleCalendarProperties;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.staff.service.StaffLookup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarSyncService {

    private final GoogleCalendarProperties properties;
    private final StaffRepository staffRepository;
    private final StaffLookup staffLookup;
    private final GoogleOAuthStateSigner oauthStateSigner;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;
    private final GoogleCalendarSyncExecutor syncExecutor;
    private final GoogleCalendarClientFactory calendarClientFactory;

    public String buildAuthorizationUrl(String staffId) {
        GoogleAuthorizationCodeFlow flow = calendarClientFactory.buildFlow();
        return flow.newAuthorizationUrl()
                .setRedirectUri(properties.getRedirectUri())
                .setState(oauthStateSigner.sign(UUID.fromString(staffId)))
                .setAccessType("offline")
                .set("prompt", "consent")
                .build();
    }

    @Transactional
    @BypassTenantFilter
    public UUID handleCallback(String code, String state) {
        UUID staffId = oauthStateSigner.verify(state);
        completeOAuthConnection(code, staffId);
        return staffId;
    }

    private void completeOAuthConnection(String code, UUID staffId) {
        try {
            GoogleTokenResponse tokenResponse = calendarClientFactory.exchangeAuthorizationCode(code);

            Staff staff = staffRepository.findByIdAndDeletedAtIsNull(staffId)
                    .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

            staff.setGoogleAccessToken(tokenResponse.getAccessToken());
            staff.setGoogleRefreshToken(tokenResponse.getRefreshToken());
            staff.setGoogleCalendarId("primary");
            staff.setGoogleTokenExpiresAt(
                    java.time.Instant.now().plusSeconds(tokenResponse.getExpiresInSeconds())
            );

            String email = fetchCalendarEmail(tokenResponse.getAccessToken());
            staff.setGoogleCalendarEmail(email);

            staffRepository.save(staff);
            log.info("Google Calendar connected for staff {}", staffId);
            auditRecorder.record(
                    AuditAction.UPDATE,
                    AuditEntityType.STAFF,
                    staff.getId().toString(),
                    auditLabelFormatter.staff(staff),
                    null,
                    "Google Calendar підключено"
            );
        } catch (Exception exception) {
            log.error("Failed to handle Google OAuth callback for staff {}: {}", staffId, exception.getMessage());
            throw new BusinessRuleException("Google OAuth callback failed: " + exception.getMessage());
        }
    }

    @Transactional
    public void disconnect(UUID staffId) {
        Staff staff = staffLookup.requireStaff(staffId);
        staff.setGoogleAccessToken(null);
        staff.setGoogleRefreshToken(null);
        staff.setGoogleCalendarId(null);
        staff.setGoogleTokenExpiresAt(null);
        staff.setGoogleCalendarEmail(null);
        staffRepository.save(staff);
        log.info("Google Calendar disconnected for staff {}", staffId);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.STAFF,
                staff.getId().toString(),
                auditLabelFormatter.staff(staff),
                null,
                "Google Calendar відключено"
        );
    }

    @Async
    public void syncNewAppointment(Appointment appointment) {
        syncExecutor.syncNewAppointment(appointment.getTenantId(), appointment.getId());
    }

    @Async
    public void syncUpdatedAppointment(Appointment appointment) {
        syncExecutor.syncUpdatedAppointment(appointment.getTenantId(), appointment.getId());
    }

    @Async
    public void syncDeletedAppointment(Appointment appointment) {
        Staff artist = appointment.getArtist();
        syncExecutor.syncDeletedAppointment(
                appointment.getTenantId(),
                appointment.getId(),
                appointment.getGoogleEventId(),
                artist != null ? artist.getId() : null
        );
    }

    Calendar getCalendarService(Staff artist) throws Exception {
        return calendarClientFactory.getCalendarService(artist);
    }

    GoogleTokenResponse exchangeAuthorizationCode(String code) throws java.io.IOException {
        return calendarClientFactory.exchangeAuthorizationCode(code);
    }

    GoogleTokenResponse refreshAccessToken(String refreshToken) throws java.io.IOException {
        return calendarClientFactory.refreshAccessToken(refreshToken);
    }

    private String fetchCalendarEmail(String accessToken) {
        try {
            return calendarClientFactory.getCalendarService(accessToken)
                    .calendars()
                    .get("primary")
                    .execute()
                    .getId();
        } catch (Exception exception) {
            log.warn("Could not fetch Google Calendar email: {}", exception.getMessage());
            return null;
        }
    }
}
