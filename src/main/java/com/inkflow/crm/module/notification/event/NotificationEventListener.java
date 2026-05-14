package com.inkflow.crm.module.notification.event;

import com.inkflow.crm.module.notification.entity.NotificationType;
import com.inkflow.crm.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
            .withZone(ZoneId.of("Europe/Kyiv"));

    @Async
    @EventListener
    public void onNewRequest(NewRequestEvent event) {
        log.info("Notification: new request {} for staff {}", event.requestId(), event.staffId());

        String title = "Новий запит на запис";
        String body = event.clientName() + (event.idea() != null ? ": " + truncate(event.idea(), 80) : "");

        notificationService.send(
                event.tenantId(),
                event.staffId(),
                NotificationType.NEW_REQUEST,
                title,
                body,
                Map.of("requestId", event.requestId().toString(), "type", "new_request")
        );
    }

    @Async
    @EventListener
    public void onAppointmentReminder(AppointmentReminderEvent event) {
        log.info("Notification: appointment reminder {} for staff {}", event.appointmentId(), event.staffId());

        String time = TIME_FMT.format(event.startTime());
        String title = "Нагадування про запис";
        String body = event.clientName() + " о " + time;

        notificationService.send(
                event.tenantId(),
                event.staffId(),
                NotificationType.APPOINTMENT_REMINDER,
                title,
                body,
                Map.of("appointmentId", event.appointmentId().toString(), "type", "reminder")
        );
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
