package com.inkflow.crm.module.notification.event;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.module.notification.entity.NotificationType;
import com.inkflow.crm.module.notification.service.NotificationService;
import com.inkflow.crm.module.notification.support.PushPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private InkflowProperties inkflowProperties;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void shouldSendNewRequestNotificationWithoutIdeaSuffix() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        listener.onNewRequest(new NewRequestEvent(requestId, tenantId, staffId, "Anna Client", null));

        verify(notificationService).send(
                eq(tenantId),
                eq(staffId),
                eq(NotificationType.NEW_REQUEST),
                eq("Новий запит на запис"),
                eq("Anna Client"),
                eq(PushPayload.forRequest(PushPayload.TYPE_NEW_REQUEST, requestId, tenantId))
        );
    }

    @Test
    void shouldTruncateLongIdeaInNewRequestBody() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String longIdea = "a".repeat(100);

        listener.onNewRequest(new NewRequestEvent(requestId, tenantId, staffId, "Anna Client", longIdea));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).send(
                eq(tenantId),
                eq(staffId),
                eq(NotificationType.NEW_REQUEST),
                eq("Новий запит на запис"),
                bodyCaptor.capture(),
                eq(PushPayload.forRequest(PushPayload.TYPE_NEW_REQUEST, requestId, tenantId))
        );

        String body = bodyCaptor.getValue();
        assertTrue(body.startsWith("Anna Client: "));
        assertTrue(body.endsWith("…"));
        assertEquals(80 + "Anna Client: ".length() + 1, body.length());
    }

    @Test
    void shouldSendClientRequestMessageNotification() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        listener.onClientRequestMessage(new ClientRequestMessageEvent(
                requestId, tenantId, staffId, "Anna Client", "Можна завтра?"
        ));

        verify(notificationService).send(
                eq(tenantId),
                eq(staffId),
                eq(NotificationType.REQUEST_MESSAGE),
                eq("Нове повідомлення від клієнта"),
                eq("Anna Client: Можна завтра?"),
                eq(PushPayload.forRequest(PushPayload.TYPE_REQUEST_MESSAGE, requestId, tenantId))
        );
    }

    @Test
    void shouldSendAppointmentReminderWithFormattedTime() {
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Instant startTime = Instant.parse("2026-06-11T14:30:00Z");

        when(inkflowProperties.defaultZoneId()).thenReturn(java.time.ZoneId.of("Europe/Kyiv"));

        listener.onAppointmentReminder(new AppointmentReminderEvent(
                appointmentId, tenantId, staffId, UUID.randomUUID(), "John Doe", startTime
        ));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).send(
                eq(tenantId),
                eq(staffId),
                eq(NotificationType.APPOINTMENT_REMINDER),
                eq("Нагадування про запис"),
                bodyCaptor.capture(),
                dataCaptor.capture()
        );

        assertTrue(bodyCaptor.getValue().startsWith("John Doe о "));
        assertEquals(
                PushPayload.forAppointment(PushPayload.TYPE_APPOINTMENT_REMINDER, appointmentId, tenantId),
                dataCaptor.getValue()
        );
    }
}
