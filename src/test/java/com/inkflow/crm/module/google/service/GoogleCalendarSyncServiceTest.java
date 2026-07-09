package com.inkflow.crm.module.google.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.config.GoogleCalendarProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.staff.service.StaffLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarSyncServiceTest {

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REDIRECT_URI = "https://app.example.com/google/callback";

    @Mock
    private GoogleCalendarProperties properties;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private StaffLookup staffLookup;

    @Mock
    private GoogleOAuthStateSigner oauthStateSigner;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @Mock
    private GoogleCalendarSyncExecutor syncExecutor;

    @Mock
    private GoogleCalendarClientFactory calendarClientFactory;

    @InjectMocks
    private GoogleCalendarSyncService googleCalendarSyncService;

    @BeforeEach
    void stubAuditLabels() {
        lenient().when(auditLabelFormatter.staff(any())).thenReturn("Staff");
        lenient().when(properties.getRedirectUri()).thenReturn(REDIRECT_URI);
    }

    private void configureOAuthProperties() {
        when(properties.getClientId()).thenReturn(CLIENT_ID);
        when(properties.getClientSecret()).thenReturn(CLIENT_SECRET);
    }

    @Test
    void handleCallback_shouldThrowBusinessRuleExceptionWhenTokenExchangeFails() throws Exception {
        UUID staffId = UUID.randomUUID();
        when(oauthStateSigner.verify("signed-state")).thenReturn(staffId);
        when(calendarClientFactory.exchangeAuthorizationCode("auth-code"))
                .thenThrow(new IOException("invalid_grant"));

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> googleCalendarSyncService.handleCallback("auth-code", "signed-state")
        );

        assertTrue(ex.getMessage().contains("Google OAuth callback failed"));
        assertTrue(ex.getMessage().contains("invalid_grant"));
        verify(staffRepository, never()).save(any());
    }

    @Test
    void disconnect_clearsGoogleTokens() {
        UUID staffId = UUID.randomUUID();
        Staff staff = Staff.builder()
                .id(staffId)
                .googleAccessToken("access")
                .googleRefreshToken("refresh")
                .googleCalendarId("primary")
                .googleCalendarEmail("artist@gmail.com")
                .googleTokenExpiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(staffLookup.requireStaff(staffId)).thenReturn(staff);

        googleCalendarSyncService.disconnect(staffId);

        assertNull(staff.getGoogleAccessToken());
        assertNull(staff.getGoogleRefreshToken());
        assertNull(staff.getGoogleCalendarId());
        assertNull(staff.getGoogleCalendarEmail());
        assertNull(staff.getGoogleTokenExpiresAt());
        verify(staffRepository).save(staff);
    }

    @Test
    void syncNewAppointment_delegatesToExecutor() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .build();

        googleCalendarSyncService.syncNewAppointment(appointment);

        verify(syncExecutor).syncNewAppointment(tenantId, appointmentId);
    }

    @Test
    void syncUpdatedAppointment_delegatesToExecutor() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .build();

        googleCalendarSyncService.syncUpdatedAppointment(appointment);

        verify(syncExecutor).syncUpdatedAppointment(tenantId, appointmentId);
    }

    @Test
    void syncDeletedAppointment_delegatesToExecutor() {
        UUID tenantId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();
        Appointment appointment = Appointment.builder()
                .id(appointmentId)
                .tenantId(tenantId)
                .googleEventId("event-id")
                .artist(Staff.builder().id(artistId).build())
                .build();

        googleCalendarSyncService.syncDeletedAppointment(appointment);

        verify(syncExecutor).syncDeletedAppointment(tenantId, appointmentId, "event-id", artistId);
    }

    @Test
    void getCalendarService_delegatesToFactory() throws Exception {
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Calendar calendar = org.mockito.Mockito.mock(Calendar.class);
        when(calendarClientFactory.getCalendarService(artist)).thenReturn(calendar);

        Calendar result = googleCalendarSyncService.getCalendarService(artist);

        assertEquals(calendar, result);
    }
}
