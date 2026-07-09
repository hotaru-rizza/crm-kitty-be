package com.inkflow.crm.module.google.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarSyncExecutorTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private InkflowProperties inkflowProperties;

    @Mock
    private GoogleCalendarEventBuilder eventBuilder;

    @Mock
    private GoogleCalendarClientFactory calendarClientFactory;

    @InjectMocks
    private GoogleCalendarSyncExecutor syncExecutor;

    @BeforeEach
    void configureTimezone() {
        lenient().when(inkflowProperties.getDefaultTimezone()).thenReturn("Europe/Kyiv");
    }

    @Test
    void syncNewAppointment_skipsWhenArtistNotConnected() {
        Staff artist = Staff.builder().id(UUID.randomUUID()).build();
        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .artist(artist)
                .build();

        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointment.getId())).thenReturn(Optional.of(appointment));

        syncExecutor.syncNewAppointment(appointment.getTenantId(), appointment.getId());

        verifyNoInteractions(calendarClientFactory);
    }

    @Test
    void syncNewAppointment_createsEventAndPersistsEventIdWhenConnected() throws Exception {
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);
        Event event = new Event().setSummary("Tattoo Session — John Doe");
        Calendar calendar = GoogleCalendarApiMocks.calendarWithInsert("google-event-456");

        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointment.getId())).thenReturn(Optional.of(appointment));
        when(eventBuilder.buildEvent(appointment, "Europe/Kyiv")).thenReturn(event);
        when(calendarClientFactory.getCalendarService(artist)).thenReturn(calendar);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        syncExecutor.syncNewAppointment(appointment.getTenantId(), appointment.getId());

        assertEquals("google-event-456", appointment.getGoogleEventId());
        verify(appointmentRepository).save(appointment);
    }

    @Test
    void syncNewAppointment_buildsEventWithClientServiceAndLocation() throws Exception {
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);
        ArgumentCaptor<Event> eventCaptor = ArgumentCaptor.forClass(Event.class);
        Event event = new Event()
                .setSummary("Tattoo Session — John Doe")
                .setLocation("Kyiv, Main St 1")
                .setDescription("John Doe Tattoo Session 1500 Bring reference");
        Calendar calendar = GoogleCalendarApiMocks.calendarWithInsert("event-id");

        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointment.getId())).thenReturn(Optional.of(appointment));
        when(eventBuilder.buildEvent(appointment, "Europe/Kyiv")).thenReturn(event);
        when(calendarClientFactory.getCalendarService(artist)).thenReturn(calendar);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);

        syncExecutor.syncNewAppointment(appointment.getTenantId(), appointment.getId());

        verify(calendar.events()).insert(eq("primary"), eventCaptor.capture());
        Event captured = eventCaptor.getValue();
        assertEquals("Tattoo Session — John Doe", captured.getSummary());
        assertEquals("Kyiv, Main St 1", captured.getLocation());
    }

    @Test
    void syncNewAppointment_swallowsCalendarApiFailure() throws Exception {
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);

        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointment.getId())).thenReturn(Optional.of(appointment));
        when(eventBuilder.buildEvent(appointment, "Europe/Kyiv")).thenReturn(new Event());
        Calendar failingCalendar = GoogleCalendarApiMocks.calendarWithFailingInsert(new IOException("API down"));
        when(calendarClientFactory.getCalendarService(artist)).thenReturn(failingCalendar);

        syncExecutor.syncNewAppointment(appointment.getTenantId(), appointment.getId());

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void syncUpdatedAppointment_skipsWhenNoGoogleEventId() {
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);

        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointment.getId())).thenReturn(Optional.of(appointment));

        syncExecutor.syncUpdatedAppointment(appointment.getTenantId(), appointment.getId());

        verifyNoInteractions(calendarClientFactory);
    }

    @Test
    void syncUpdatedAppointment_updatesExistingEventWhenConnected() throws Exception {
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        Appointment appointment = GoogleCalendarTestFixtures.connectedAppointment(artist);
        appointment.setGoogleEventId("existing-event-id");
        Calendar calendar = GoogleCalendarApiMocks.calendarWithUpdate();

        when(appointmentRepository.findByIdAndDeletedAtIsNull(appointment.getId())).thenReturn(Optional.of(appointment));
        when(eventBuilder.buildEvent(appointment, "Europe/Kyiv")).thenReturn(new Event());
        when(calendarClientFactory.getCalendarService(artist)).thenReturn(calendar);

        syncExecutor.syncUpdatedAppointment(appointment.getTenantId(), appointment.getId());

        verify(calendar.events()).update(eq("primary"), eq("existing-event-id"), any(Event.class));
    }

    @Test
    void syncDeletedAppointment_deletesExistingEventWhenConnected() throws Exception {
        Staff artist = GoogleCalendarTestFixtures.connectedArtist();
        UUID appointmentId = UUID.randomUUID();
        Calendar calendar = GoogleCalendarApiMocks.calendarWithDelete();

        when(staffRepository.findByIdAndDeletedAtIsNull(artist.getId())).thenReturn(Optional.of(artist));
        when(calendarClientFactory.getCalendarService(artist)).thenReturn(calendar);

        syncExecutor.syncDeletedAppointment(
                UUID.randomUUID(),
                appointmentId,
                "existing-event-id",
                artist.getId()
        );

        verify(calendar.events()).delete("primary", "existing-event-id");
    }

    @Test
    void syncDeletedAppointment_skipsWhenNoGoogleEventId() {
        syncExecutor.syncDeletedAppointment(UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID());

        verifyNoInteractions(staffRepository, calendarClientFactory);
    }
}
