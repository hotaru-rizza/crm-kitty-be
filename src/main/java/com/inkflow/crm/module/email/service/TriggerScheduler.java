package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.common.scheduler.SchedulerRunService;
import com.inkflow.crm.config.BypassTenantFilter;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.EmailTemplateRepository;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.NotificationDispatchContext;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Component
@BypassTenantFilter
@RequiredArgsConstructor
public class TriggerScheduler {

    private static final String JOB_KEY = "TRIGGER_SCHEDULER";

    private final EmailTemplateRepository emailTemplateRepository;
    private final ScheduledTriggerQueryService scheduledTriggerQueryService;
    private final EmailTenantContextLoader tenantContextLoader;
    private final TriggerVariableBuilder variableBuilder;
    private final NotificationDispatcher notificationDispatcher;
    private final SchedulerRunService schedulerRunService;
    private final InkflowProperties properties;

    @Scheduled(fixedRateString = "${inkflow.email.scheduler.fixed-rate-ms:900000}")
    public void processScheduledTriggers() {
        Instant now = Instant.now();
        Instant lastRun = resolveLastRun(now);
        log.debug("Running trigger scheduler: window ({} .. {}]", lastRun, now);

        processBeforeBooking(lastRun, now);
        processAfterBooking(lastRun, now);
        processClientBirthday(now);
        processClientInactive(now);

        schedulerRunService.markRun(JOB_KEY, now);
    }

    private Instant resolveLastRun(Instant now) {
        InkflowProperties.Scheduler scheduler = properties.getEmail().getScheduler();
        Instant fallback = now.minusMillis(scheduler.getFixedRateMs());
        Instant lastRun = schedulerRunService.lastRun(JOB_KEY).orElse(fallback);

        Instant earliest = now.minus(scheduler.getCatchUpMaxHours(), ChronoUnit.HOURS);
        return lastRun.isBefore(earliest) ? earliest : lastRun;
    }

    private void processBeforeBooking(Instant lastRun, Instant now) {
        for (EmailTemplate template : findEnabledScheduled(TriggerType.BEFORE_BOOKING)) {
            int offsetMinutes = requireOffset(template);
            int hoursBefore = offsetMinutes / 60;
            Instant from = lastRun.plus(hoursBefore, ChronoUnit.HOURS);
            Instant to = now.plus(hoursBefore, ChronoUnit.HOURS);

            for (Appointment appointment : withTenant(template.getTenantId(),
                    () -> scheduledTriggerQueryService.findAppointmentsByDateRange(from, to))) {
                if (isEligibleForReminder(appointment)) {
                    enqueueSafely(appointment, TriggerType.BEFORE_BOOKING, offsetMinutes);
                }
            }
        }
    }

    private void processAfterBooking(Instant lastRun, Instant now) {
        for (EmailTemplate template : findEnabledScheduled(TriggerType.AFTER_BOOKING)) {
            int offsetMinutes = requireOffset(template);
            int hoursAfter = offsetMinutes / 60;
            Instant from = lastRun.minus(hoursAfter, ChronoUnit.HOURS);
            Instant to = now.minus(hoursAfter, ChronoUnit.HOURS);

            for (Appointment appointment : withTenant(template.getTenantId(),
                    () -> scheduledTriggerQueryService.findAppointmentsByDateRange(from, to))) {
                if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
                    enqueueSafely(appointment, TriggerType.AFTER_BOOKING, offsetMinutes);
                }
            }
        }
    }

    private void processClientBirthday(Instant now) {
        LocalDate today = now.atZone(properties.defaultZoneId()).toLocalDate();

        for (EmailTemplate template : findEnabledScheduled(TriggerType.CLIENT_BIRTHDAY)) {
            List<Client> clients = withTenant(template.getTenantId(),
                    () -> scheduledTriggerQueryService.findClientsByBirthDate(today));

            for (Client client : clients) {
                if (client.getEmail() == null || client.getEmail().isBlank()) {
                    continue;
                }
                try {
                    EmailTenantContext tenantContext = tenantContextLoader.loadContext(template.getTenantId());
                    NotificationDispatchContext context = variableBuilder.forClient(
                            client, tenantContext, TriggerType.CLIENT_BIRTHDAY);
                    notificationDispatcher.enqueue(TriggerType.CLIENT_BIRTHDAY, context);
                } catch (Exception exception) {
                    log.error("Birthday trigger failed for client {} tenant {}: {}",
                            client.getId(), template.getTenantId(), exception.getMessage());
                }
            }
        }
    }

    private void processClientInactive(Instant now) {
        for (EmailTemplate template : findEnabledScheduled(TriggerType.CLIENT_INACTIVE)) {
            int offsetMinutes = requireOffset(template);
            Instant cutoff = now.minus(offsetMinutes, ChronoUnit.MINUTES);

            List<Client> clients = withTenant(template.getTenantId(),
                    () -> scheduledTriggerQueryService.findInactiveClients(cutoff));

            for (Client client : clients) {
                if (client.getEmail() == null || client.getEmail().isBlank()) {
                    continue;
                }
                try {
                    EmailTenantContext tenantContext = tenantContextLoader.loadContext(template.getTenantId());
                    NotificationDispatchContext context = variableBuilder.forClient(
                            client, tenantContext, TriggerType.CLIENT_INACTIVE);
                    notificationDispatcher.enqueue(TriggerType.CLIENT_INACTIVE, context);
                } catch (Exception exception) {
                    log.error("Inactive trigger failed for client {} tenant {}: {}",
                            client.getId(), template.getTenantId(), exception.getMessage());
                }
            }
        }
    }

    private List<EmailTemplate> findEnabledScheduled(TriggerType triggerType) {
        return emailTemplateRepository.findByTriggerTypeAndEnabledTrue(triggerType);
    }

    private void enqueueSafely(
            Appointment appointment,
            TriggerType triggerType,
            int offsetMinutes) {

        try {
            EmailTenantContext tenantContext = tenantContextLoader.loadContext(appointment.getTenantId());
            NotificationDispatchContext context = variableBuilder.forClient(
                    appointment, tenantContext, triggerType, offsetMinutes);
            notificationDispatcher.enqueue(triggerType, context);
        } catch (Exception exception) {
            log.error("Scheduled trigger failed: trigger={} appointment={} error={}",
                    triggerType, appointment.getId(), exception.getMessage());
        }
    }

    private int requireOffset(EmailTemplate template) {
        if (template.getOffsetMinutes() == null || template.getOffsetMinutes() <= 0) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Template " + template.getId() + " requires offset_minutes");
        }
        return template.getOffsetMinutes();
    }

    private boolean isEligibleForReminder(Appointment appointment) {
        return appointment.getStatus() == AppointmentStatus.SCHEDULED;
    }

    private <T> T withTenant(UUID tenantId, Supplier<T> action) {
        TenantContext.setCurrentTenant(tenantId);
        try {
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }
}
