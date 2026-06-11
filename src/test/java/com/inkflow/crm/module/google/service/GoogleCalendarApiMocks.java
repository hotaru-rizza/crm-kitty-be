package com.inkflow.crm.module.google.service;

import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class GoogleCalendarApiMocks {

    private GoogleCalendarApiMocks() {
    }

    static Calendar calendarWithInsert(String returnedEventId) throws IOException {
        Calendar calendar = mock(Calendar.class);
        Calendar.Events events = mock(Calendar.Events.class);
        Calendar.Events.Insert insert = mock(Calendar.Events.Insert.class);
        Event created = new Event().setId(returnedEventId);

        when(calendar.events()).thenReturn(events);
        when(events.insert(anyString(), any(Event.class))).thenReturn(insert);
        when(insert.execute()).thenReturn(created);
        return calendar;
    }

    static Calendar calendarWithUpdate() throws IOException {
        Calendar calendar = mock(Calendar.class);
        Calendar.Events events = mock(Calendar.Events.class);
        Calendar.Events.Update update = mock(Calendar.Events.Update.class);

        when(calendar.events()).thenReturn(events);
        when(events.update(anyString(), anyString(), any(Event.class))).thenReturn(update);
        when(update.execute()).thenReturn(new Event());
        return calendar;
    }

    static Calendar calendarWithDelete() throws IOException {
        Calendar calendar = mock(Calendar.class);
        Calendar.Events events = mock(Calendar.Events.class);
        Calendar.Events.Delete delete = mock(Calendar.Events.Delete.class);

        when(calendar.events()).thenReturn(events);
        when(events.delete(anyString(), anyString())).thenReturn(delete);
        doNothing().when(delete).execute();
        return calendar;
    }

    static Calendar calendarWithFailingInsert(IOException error) throws IOException {
        Calendar calendar = mock(Calendar.class);
        Calendar.Events events = mock(Calendar.Events.class);
        Calendar.Events.Insert insert = mock(Calendar.Events.Insert.class);

        when(calendar.events()).thenReturn(events);
        when(events.insert(anyString(), any(Event.class))).thenReturn(insert);
        when(insert.execute()).thenThrow(error);
        return calendar;
    }

    static Calendar calendarWithFailingUpdate(IOException error) throws IOException {
        Calendar calendar = mock(Calendar.class);
        Calendar.Events events = mock(Calendar.Events.class);
        Calendar.Events.Update update = mock(Calendar.Events.Update.class);

        when(calendar.events()).thenReturn(events);
        when(events.update(anyString(), anyString(), any(Event.class))).thenReturn(update);
        when(update.execute()).thenThrow(error);
        return calendar;
    }
}
