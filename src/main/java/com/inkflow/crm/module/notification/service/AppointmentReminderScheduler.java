package com.inkflow.crm.module.notification.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.notification.event.AppointmentReminderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentReminderScheduler {

    private final AppointmentRepository appointmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    private final Set<UUID> sentReminders = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedRate = 300_000)
    public void checkUpcomingAppointments() {
        Instant now = Instant.now();
        Instant oneHourFromNow = now.plus(1, ChronoUnit.HOURS);

        List<Appointment> upcoming = appointmentRepository.findUpcomingForReminders(now, oneHourFromNow);

        for (Appointment apt : upcoming) {
            if (sentReminders.contains(apt.getId())) continue;
            sentReminders.add(apt.getId());

            eventPublisher.publishEvent(new AppointmentReminderEvent(
                    apt.getId(),
                    apt.getTenantId(),
                    apt.getArtist().getId(),
                    apt.getClient().getId(),
                    apt.getClient().getFirstName() + " " + apt.getClient().getLastName(),
                    apt.getStartTime()
            ));
        }

        sentReminders.removeIf(id -> !upcoming.stream().anyMatch(a -> a.getId().equals(id)));
    }
}
