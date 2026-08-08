package com.inkflow.crm.module.notification.event;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.module.notification.entity.NotificationType;
import com.inkflow.crm.module.notification.service.NotificationService;
import com.inkflow.crm.module.notification.support.PushPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private static final int PREVIEW_MAX_LENGTH = 80;

    private final NotificationService notificationService;
    private final InkflowProperties inkflowProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNewRequest(NewRequestEvent event) {
        log.info("Notification: new request {} for staff {}", event.requestId(), event.staffId());

        String title = "Новий запит на запис";
        String body = event.clientName()
                + (event.idea() != null ? ": " + truncate(event.idea(), PREVIEW_MAX_LENGTH) : "");

        Map<String, String> data = PushPayload.forRequest(
                PushPayload.TYPE_NEW_REQUEST,
                event.requestId(),
                event.tenantId()
        );

        notificationService.send(
                event.tenantId(),
                event.staffId(),
                NotificationType.NEW_REQUEST,
                title,
                body,
                data
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onClientRequestMessage(ClientRequestMessageEvent event) {
        log.info("Notification: client message on request {} for staff {}",
                event.requestId(), event.staffId());

        String title = "Нове повідомлення від клієнта";
        String body = event.clientName()
                + (event.preview() != null && !event.preview().isBlank()
                ? ": " + truncate(event.preview(), PREVIEW_MAX_LENGTH)
                : "");

        Map<String, String> data = PushPayload.forRequest(
                PushPayload.TYPE_REQUEST_MESSAGE,
                event.requestId(),
                event.tenantId()
        );

        notificationService.send(
                event.tenantId(),
                event.staffId(),
                NotificationType.REQUEST_MESSAGE,
                title,
                body,
                data
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAppointmentReminder(AppointmentReminderEvent event) {
        log.info("Notification: appointment reminder {} for staff {}",
                event.appointmentId(), event.staffId());

        String time = timeFormatter().format(event.startTime());
        String title = "Нагадування про запис";
        String body = event.clientName() + " о " + time;

        Map<String, String> data = PushPayload.forAppointment(
                PushPayload.TYPE_APPOINTMENT_REMINDER,
                event.appointmentId(),
                event.tenantId()
        );

        notificationService.send(
                event.tenantId(),
                event.staffId(),
                NotificationType.APPOINTMENT_REMINDER,
                title,
                body,
                data
        );
    }

    private DateTimeFormatter timeFormatter() {
        return DateTimeFormatter.ofPattern("HH:mm").withZone(inkflowProperties.defaultZoneId());
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
