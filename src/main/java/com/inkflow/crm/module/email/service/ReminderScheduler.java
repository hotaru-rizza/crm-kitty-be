package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.CompanySettings;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.enums.EmailType;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.CompanySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {

    private final AppointmentRepository appointmentRepository;
    private final CompanySettingsRepository companySettingsRepository;
    private final EmailService emailService;

    @Scheduled(fixedRate = 900_000) // every 15 minutes
    public void processReminders() {
        log.debug("Running reminder scheduler...");

        List<CompanySettings> allSettings = companySettingsRepository.findAll();

        for (CompanySettings settings : allSettings) {
            try {
                if (settings.getEmailReminders()) {
                    processReminderEmails(settings);
                }
                if (settings.getEmailAftercare()) {
                    processAftercareEmails(settings);
                }
            } catch (Exception e) {
                log.error("Scheduler error for tenant {}: {}", settings.getTenantId(), e.getMessage());
            }
        }
    }

    private void processReminderEmails(CompanySettings settings) {
        int hoursBefore = settings.getReminderHoursBefore();
        Instant now = Instant.now();
        Instant windowStart = now.plus(hoursBefore, ChronoUnit.HOURS).minus(15, ChronoUnit.MINUTES);
        Instant windowEnd = now.plus(hoursBefore, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES);

        List<Appointment> appointments = appointmentRepository.findByTenantIdAndDateRange(
                settings.getTenantId(), windowStart, windowEnd
        );

        for (Appointment appointment : appointments) {
            if (!isEligibleForReminder(appointment)) continue;
            if (emailService.wasAlreadySent(appointment.getId(), EmailType.REMINDER)) continue;

            try {
                emailService.sendReminder(appointment, hoursBefore);
            } catch (Exception e) {
                log.error("Failed to send reminder for appointment {}: {}", appointment.getId(), e.getMessage());
            }
        }
    }

    private void processAftercareEmails(CompanySettings settings) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(24, ChronoUnit.HOURS).minus(15, ChronoUnit.MINUTES);
        Instant windowEnd = now.minus(24, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES);

        List<Appointment> appointments = appointmentRepository.findByTenantIdAndDateRange(
                settings.getTenantId(), windowStart, windowEnd
        );

        for (Appointment appointment : appointments) {
            if (appointment.getStatus() != AppointmentStatus.DONE) continue;
            if (emailService.wasAlreadySent(appointment.getId(), EmailType.AFTERCARE)) continue;

            try {
                emailService.sendAftercare(appointment);
            } catch (Exception e) {
                log.error("Failed to send aftercare for appointment {}: {}", appointment.getId(), e.getMessage());
            }
        }
    }

    private boolean isEligibleForReminder(Appointment appointment) {
        return appointment.getStatus() == AppointmentStatus.NEW
                || appointment.getStatus() == AppointmentStatus.CONFIRMED;
    }
}
