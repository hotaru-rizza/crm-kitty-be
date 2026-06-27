package com.inkflow.crm.module.google.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.config.GoogleCalendarProperties;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.staff.service.StaffLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
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
    private InkflowProperties inkflowProperties;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private StaffLookup staffLookup;

    @Mock
    private GoogleOAuthStateSigner oauthStateSigner;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private AuditLabelFormatter auditLabelFormatter;

    @InjectMocks
    private GoogleCalendarSyncService googleCalendarSyncService;

    @BeforeEach
    void stubAuditLabels() {
        lenient().when(auditLabelFormatter.staff(any())).thenReturn("Staff");
    }

    private void initGoogleTransport() {
        googleCalendarSyncService.init();
    }

    private void configureOAuthProperties() {
        when(properties.getClientId()).thenReturn(CLIENT_ID);
        when(properties.getClientSecret()).thenReturn(CLIENT_SECRET);
        when(properties.getRedirectUri()).thenReturn(REDIRECT_URI);
        googleCalendarSyncService.init();
    }

    private void configureTimezone() {
        when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
    }

    @Test
    void buildAuthorizationUrl_includesClientRedirectUriAndSignedState() {
        configureOAuthProperties();
        UUID staffId = UUID.randomUUID();
        when(oauthStateSigner.sign(staffId)).thenReturn("signed-state-token");

        String url = googleCalendarSyncService.buildAuthorizationUrl(staffId.toString());

        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/auth?"));
        assertTrue(url.contains("client_id=" + CLIENT_ID));
        assertTrue(url.contains("state=signed-state-token"));
        assertTrue(url.contains("access_type=offline"));
        assertTrue(url.contains("prompt=consent"));
        assertTrue(url.contains("redirect_uri="));
        assertTrue(url.contains("app.example.com"));
        verify(oauthStateSigner).sign(staffId);
    }

    @Test
    void handleCallback_shouldThrowBusinessRuleExceptionWhenTokenExchangeFails() throws Exception {
        UUID staffId = UUID.randomUUID();
        when(oauthStateSigner.verify("signed-state")).thenReturn(staffId);
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);

        doThrow(new IOException("invalid_grant"))
                .when(spy)
                .exchangeAuthorizationCode("auth-code");

        BusinessRuleException ex = assertThrows(
                BusinessRuleException.class,
                () -> spy.handleCallback("auth-code", "signed-state")
        );

        assertTrue(ex.getMessage().contains("Google OAuth callback failed"));
        assertTrue(ex.getMessage().contains("invalid_grant"));
        verify(staffRepository, never()).save(any());
    }

    @Test
    void getCalendarService_shouldReuseValidAccessTokenWithoutRefresh() throws Exception {
        initGoogleTransport();
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        artist.setGoogleTokenExpiresAt(Instant.now().plusSeconds(3600));

        var calendar = googleCalendarSyncService.getCalendarService(artist);

        assertNotNull(calendar);
        verify(staffRepository, never()).save(any());
    }

    @Test
    void getCalendarService_shouldRefreshExpiredTokenAndPersistStaff() throws Exception {
        initGoogleTransport();
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        artist.setGoogleTokenExpiresAt(Instant.now().plusSeconds(30));
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);
        GoogleTokenResponse refreshed = GoogleCalendarTestFixtures.tokenResponse("new-access-token", 3600L);

        doReturn(refreshed).when(spy).refreshAccessToken("refresh-token");
        when(staffRepository.save(artist)).thenReturn(artist);

        var calendar = spy.getCalendarService(artist);

        assertNotNull(calendar);
        assertEquals("new-access-token", artist.getGoogleAccessToken());
        verify(staffRepository).save(artist);
    }

    @Test
    void getCalendarService_shouldRefreshWhenTokenExpiryIsNull() throws Exception {
        initGoogleTransport();
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        artist.setGoogleTokenExpiresAt(null);
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);
        GoogleTokenResponse refreshed = GoogleCalendarTestFixtures.tokenResponse("new-access-token", 3600L);

        doReturn(refreshed).when(spy).refreshAccessToken("refresh-token");
        when(staffRepository.save(artist)).thenReturn(artist);

        spy.getCalendarService(artist);

        verify(staffRepository).save(artist);
    }

    @Test
    void syncNewAppointment_buildsEventWithDefaultsWhenOptionalFieldsMissing() throws Exception {
        configureTimezone();
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.minimalAppointment(artist);
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        var calendar = GoogleCalendarApiMocks.calendarWithInsert("event-id");

        doReturn(calendar).when(spy).getCalendarService(artist);
        when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        spy.syncNewAppointment(appointment);

        verify(calendar.events()).insert(eq("primary"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertEquals("Запис — Клієнт", event.getSummary());
        assertNull(event.getLocation());
        assertTrue(event.getDescription().contains("INKAT CRM"));
        assertTrue(event.getDescription().contains("автоматичний запис"));
    }

    @Test
    void syncNewAppointment_omitsLocationWhenAddressMissing() throws Exception {
        configureTimezone();
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);
        appointment.setLocation(com.inkflow.crm.domain.entity.Location.builder().address(null).build());
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        var calendar = GoogleCalendarApiMocks.calendarWithInsert("event-id");

        doReturn(calendar).when(spy).getCalendarService(artist);
        when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        spy.syncNewAppointment(appointment);

        verify(calendar.events()).insert(eq("primary"), eventCaptor.capture());
        assertNull(eventCaptor.getValue().getLocation());
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
    void saveEventId_persistsGoogleEventReference() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = Appointment.builder().id(appointmentId).build();

        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        googleCalendarSyncService.saveEventId(appointmentId, "google-event-123");

        assertEquals("google-event-123", appointment.getGoogleEventId());
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void saveEventId_skipsWhenAppointmentNotFound() {
        UUID appointmentId = UUID.randomUUID();
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.empty());

        googleCalendarSyncService.saveEventId(appointmentId, "google-event-123");

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void syncNewAppointment_skipsWhenArtistNotConnected() {
        Staff artist = Staff.builder().id(UUID.randomUUID()).build();
        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(artist)
                .build();

        googleCalendarSyncService.syncNewAppointment(appointment);

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void syncNewAppointment_createsEventAndPersistsEventIdWhenConnected() throws Exception {
        configureTimezone();
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);

        doReturn(GoogleCalendarApiMocks.calendarWithInsert("google-event-456"))
                .when(spy)
                .getCalendarService(artist);
        when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        spy.syncNewAppointment(appointment);

        assertEquals("google-event-456", appointment.getGoogleEventId());
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void syncNewAppointment_buildsEventWithClientServiceAndLocation() throws Exception {
        configureTimezone();
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        var calendar = GoogleCalendarApiMocks.calendarWithInsert("event-id");

        doReturn(calendar).when(spy).getCalendarService(artist);
        when(appointmentRepository.findById(appointment.getId())).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        spy.syncNewAppointment(appointment);

        verify(calendar.events()).insert(eq("primary"), eventCaptor.capture());
        Event event = eventCaptor.getValue();
        assertEquals("Tattoo Session — John Doe", event.getSummary());
        assertEquals("Kyiv, Main St 1", event.getLocation());
        assertTrue(event.getDescription().contains("John Doe"));
        assertTrue(event.getDescription().contains("Tattoo Session"));
        assertTrue(event.getDescription().contains("1500"));
        assertTrue(event.getDescription().contains("Bring reference"));
        assertEquals("Europe/Kyiv", event.getStart().getTimeZone());
    }

    @Test
    void syncNewAppointment_swallowsCalendarApiFailure() throws Exception {
        configureTimezone();
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);

        doReturn(GoogleCalendarApiMocks.calendarWithFailingInsert(new IOException("API down")))
                .when(spy)
                .getCalendarService(artist);

        spy.syncNewAppointment(appointment);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void syncUpdatedAppointment_skipsWhenArtistNotConnected() {
        Staff artist = Staff.builder().id(UUID.randomUUID()).build();
        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(artist)
                .googleEventId("existing-event")
                .build();

        googleCalendarSyncService.syncUpdatedAppointment(appointment);

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void syncUpdatedAppointment_skipsWhenNoGoogleEventId() {
        Staff artist = Staff.builder()
                .id(UUID.randomUUID())
                .googleRefreshToken("refresh-token")
                .build();
        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(artist)
                .build();

        googleCalendarSyncService.syncUpdatedAppointment(appointment);

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void syncUpdatedAppointment_updatesExistingEventWhenConnected() throws Exception {
        configureTimezone();
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);
        appointment.setGoogleEventId("existing-event-id");
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);
        var calendar = GoogleCalendarApiMocks.calendarWithUpdate();

        doReturn(calendar).when(spy).getCalendarService(artist);

        spy.syncUpdatedAppointment(appointment);

        verify(calendar.events()).update(eq("primary"), eq("existing-event-id"), any(Event.class));
    }

    @Test
    void syncUpdatedAppointment_swallowsCalendarApiFailure() throws Exception {
        configureTimezone();
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);
        appointment.setGoogleEventId("existing-event-id");
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);

        doReturn(GoogleCalendarApiMocks.calendarWithFailingUpdate(new IOException("API down")))
                .when(spy)
                .getCalendarService(artist);

        spy.syncUpdatedAppointment(appointment);

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void syncDeletedAppointment_skipsWhenArtistNotConnected() {
        Staff artist = Staff.builder().id(UUID.randomUUID()).build();
        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(artist)
                .googleEventId("existing-event")
                .build();

        googleCalendarSyncService.syncDeletedAppointment(appointment);

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void syncDeletedAppointment_skipsWhenNoGoogleEventId() {
        Staff artist = Staff.builder()
                .id(UUID.randomUUID())
                .googleRefreshToken("refresh-token")
                .build();
        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .artist(artist)
                .build();

        googleCalendarSyncService.syncDeletedAppointment(appointment);

        verifyNoInteractions(appointmentRepository);
    }

    @Test
    void syncDeletedAppointment_deletesExistingEventWhenConnected() throws Exception {
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);
        appointment.setGoogleEventId("existing-event-id");
        GoogleCalendarSyncService spy = spy(googleCalendarSyncService);
        var calendar = GoogleCalendarApiMocks.calendarWithDelete();

        doReturn(calendar).when(spy).getCalendarService(artist);

        spy.syncDeletedAppointment(appointment);

        verify(calendar.events()).delete("primary", "existing-event-id");
    }
}
