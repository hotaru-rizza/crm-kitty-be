package com.inkflow.crm.module.google.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ResourceNotFoundException;
import com.inkflow.crm.config.GoogleCalendarProperties;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.staff.service.StaffLookup;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleCalendarSyncService {

    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_EVENTS);
    private static final String APP_NAME = "INKAT CRM";

    private final GoogleCalendarProperties properties;
    private final InkflowProperties inkflowProperties;
    private final StaffRepository staffRepository;
    private final StaffLookup staffLookup;
    private final GoogleOAuthStateSigner oauthStateSigner;
    private final AppointmentRepository appointmentRepository;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;

    private HttpTransport httpTransport;
    private JsonFactory jsonFactory;

    @PostConstruct
    void init() {
        // Lazy: GoogleNetHttpTransport can block minutes on slow/blocked network — not needed at startup.
    }

    private synchronized void ensureTransport() {
        if (httpTransport != null) {
            return;
        }
        try {
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            jsonFactory = JacksonFactory.getDefaultInstance();
        } catch (Exception e) {
            throw new BusinessRuleException("Google Calendar transport init failed: " + e.getMessage());
        }
    }

    public String buildAuthorizationUrl(String staffId) {
        GoogleAuthorizationCodeFlow flow = buildFlow();
        return flow.newAuthorizationUrl()
                .setRedirectUri(properties.getRedirectUri())
                .setState(oauthStateSigner.sign(UUID.fromString(staffId)))
                .setAccessType("offline")
                .set("prompt", "consent")
                .build();
    }

    @Transactional
    public void handleCallback(String code, String state) {
        UUID staffId = oauthStateSigner.verify(state);
        completeOAuthConnection(code, staffId);
    }

    private void completeOAuthConnection(String code, UUID staffId) {
        try {
            GoogleTokenResponse tokenResponse = exchangeAuthorizationCode(code);

            Staff staff = staffRepository.findById(staffId)
                    .orElseThrow(() -> ResourceNotFoundException.staff(staffId.toString()));

            staff.setGoogleAccessToken(tokenResponse.getAccessToken());
            staff.setGoogleRefreshToken(tokenResponse.getRefreshToken());
            staff.setGoogleCalendarId("primary");
            staff.setGoogleTokenExpiresAt(
                    Instant.now().plusSeconds(tokenResponse.getExpiresInSeconds())
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
        } catch (Exception e) {
            log.error("Failed to handle Google OAuth callback for staff {}: {}", staffId, e.getMessage());
            throw new BusinessRuleException("Google OAuth callback failed: " + e.getMessage());
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
        Staff artist = appointment.getArtist();
        if (!artist.isGoogleCalendarConnected()) return;

        try {
            Calendar calendarService = getCalendarService(artist);
            Event event = buildEvent(appointment);
            Event created = calendarService.events()
                    .insert(artist.getGoogleCalendarId(), event)
                    .execute();

            saveEventId(appointment.getId(), created.getId());
            log.info("Google event created: {} for appointment {}", created.getId(), appointment.getId());
        } catch (Exception e) {
            log.warn("Failed to sync new appointment {} to Google Calendar (tenantId={}): {}",
                    appointment.getId(), appointment.getTenantId(), e.getMessage());
        }
    }

    @Async
    public void syncUpdatedAppointment(Appointment appointment) {
        Staff artist = appointment.getArtist();
        if (!artist.isGoogleCalendarConnected() || appointment.getGoogleEventId() == null) return;

        try {
            Calendar calendarService = getCalendarService(artist);
            Event event = buildEvent(appointment);
            calendarService.events()
                    .update(artist.getGoogleCalendarId(), appointment.getGoogleEventId(), event)
                    .execute();

            log.info("Google event updated: {} for appointment {}", appointment.getGoogleEventId(), appointment.getId());
        } catch (Exception e) {
            log.warn("Failed to sync updated appointment {} to Google Calendar (tenantId={}): {}",
                    appointment.getId(), appointment.getTenantId(), e.getMessage());
        }
    }

    @Async
    public void syncDeletedAppointment(Appointment appointment) {
        Staff artist = appointment.getArtist();
        if (!artist.isGoogleCalendarConnected() || appointment.getGoogleEventId() == null) return;

        try {
            Calendar calendarService = getCalendarService(artist);
            calendarService.events()
                    .delete(artist.getGoogleCalendarId(), appointment.getGoogleEventId())
                    .execute();

            log.info("Google event deleted: {} for appointment {}", appointment.getGoogleEventId(), appointment.getId());
        } catch (Exception e) {
            log.warn("Failed to delete Google event for appointment {} (tenantId={}): {}",
                    appointment.getId(), appointment.getTenantId(), e.getMessage());
        }
    }

    @Transactional
    protected void saveEventId(UUID appointmentId, String googleEventId) {
        appointmentRepository.findById(appointmentId).ifPresent(a -> {
            a.setGoogleEventId(googleEventId);
            appointmentRepository.save(a);
        });
    }

    Calendar getCalendarService(Staff artist) throws Exception {
        ensureTransport();
        String accessToken = refreshTokenIfNeeded(artist);

        Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(accessToken);

        return new Calendar.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APP_NAME)
                .build();
    }

    private String refreshTokenIfNeeded(Staff artist) throws Exception {
        if (artist.getGoogleTokenExpiresAt() != null
                && artist.getGoogleTokenExpiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return artist.getGoogleAccessToken();
        }

        GoogleTokenResponse tokenResponse = refreshAccessToken(artist.getGoogleRefreshToken());

        artist.setGoogleAccessToken(tokenResponse.getAccessToken());
        artist.setGoogleTokenExpiresAt(
                Instant.now().plusSeconds(tokenResponse.getExpiresInSeconds())
        );
        staffRepository.save(artist);

        return tokenResponse.getAccessToken();
    }

    private Event buildEvent(Appointment appointment) {
        String serviceName = appointment.getService() != null ? appointment.getService().getTitle() : "Запис";
        String clientName = appointment.getClient() != null
                ? appointment.getClient().getFirstName() + " " + appointment.getClient().getLastName()
                : "Клієнт";

        Event event = new Event()
                .setSummary(serviceName + " — " + clientName)
                .setDescription(buildDescription(appointment));

        if (appointment.getLocation() != null && appointment.getLocation().getAddress() != null) {
            event.setLocation(appointment.getLocation().getAddress());
        }

        EventDateTime start = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                        java.util.Date.from(appointment.getStartTime())))
                .setTimeZone(inkflowProperties.getDefaultTimezone());
        event.setStart(start);

        EventDateTime end = new EventDateTime()
                .setDateTime(new com.google.api.client.util.DateTime(
                        java.util.Date.from(appointment.getEndTime())))
                .setTimeZone(inkflowProperties.getDefaultTimezone());
        event.setEnd(end);

        return event;
    }

    private String buildDescription(Appointment appointment) {
        StringBuilder sb = new StringBuilder();
        sb.append("INKAT CRM — автоматичний запис\n\n");

        if (appointment.getClient() != null) {
            sb.append("Клієнт: ").append(appointment.getClient().getFirstName())
                    .append(" ").append(appointment.getClient().getLastName());
            if (appointment.getClient().getPhone() != null) {
                sb.append(" (").append(appointment.getClient().getPhone()).append(")");
            }
            sb.append("\n");
        }

        if (appointment.getService() != null) {
            sb.append("Послуга: ").append(appointment.getService().getTitle()).append("\n");
        }

        if (appointment.getPrice() != null) {
            sb.append("Вартість: ").append(appointment.getPrice()).append(" грн\n");
        }

        if (appointment.getPrepayment() != null && appointment.getPrepayment().signum() > 0) {
            sb.append("Передоплата: ").append(appointment.getPrepayment()).append(" грн\n");
        }

        if (appointment.getNotes() != null && !appointment.getNotes().isBlank()) {
            sb.append("\nНотатки: ").append(appointment.getNotes()).append("\n");
        }

        return sb.toString();
    }

    private String fetchCalendarEmail(String accessToken) {
        try {
            ensureTransport();
            Credential credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
                    .setAccessToken(accessToken);
            Calendar calendarService = new Calendar.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName(APP_NAME)
                    .build();
            return calendarService.calendars().get("primary").execute().getId();
        } catch (Exception e) {
            log.warn("Could not fetch Google Calendar email: {}", e.getMessage());
            return null;
        }
    }

    GoogleTokenResponse exchangeAuthorizationCode(String code) throws IOException {
        ensureTransport();
        return new GoogleAuthorizationCodeTokenRequest(
                httpTransport, jsonFactory,
                properties.getClientId(), properties.getClientSecret(),
                code, properties.getRedirectUri()
        ).execute();
    }

    GoogleTokenResponse refreshAccessToken(String refreshToken) throws IOException {
        ensureTransport();
        return new com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest(
                httpTransport, jsonFactory,
                refreshToken,
                properties.getClientId(),
                properties.getClientSecret()
        ).execute();
    }

    private GoogleAuthorizationCodeFlow buildFlow() {
        ensureTransport();
        try {
            return new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, jsonFactory,
                    properties.getClientId(), properties.getClientSecret(),
                    SCOPES
            ).setAccessType("offline").build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Google OAuth flow", e);
        }
    }
}
