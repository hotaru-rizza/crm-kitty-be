package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.scheduler.SchedulerRunService;
import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.EmailTemplateRepository;
import com.inkflow.crm.module.email.enums.BuiltInTemplateKey;
import com.inkflow.crm.module.email.enums.TriggerType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TriggerSchedulerTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private EmailTemplateRepository emailTemplateRepository;
    @Mock private EmailTenantContextLoader tenantContextLoader;
    @Mock private TriggerVariableBuilder variableBuilder;
    @Mock private NotificationDispatcher notificationDispatcher;
    @Mock private SchedulerRunService schedulerRunService;
    @Spy private InkflowProperties properties = new InkflowProperties();

    @InjectMocks
    private TriggerScheduler triggerScheduler;

    @Test
    void processScheduledTriggers_enqueuesBeforeBookingForEligibleAppointments() {
        UUID tenantId = UUID.randomUUID();
        EmailTemplate template = EmailTemplate.builder()
                .tenantId(tenantId)
                .triggerType(TriggerType.BEFORE_BOOKING)
                .offsetMinutes(24 * 60)
                .enabled(true)
                .builtinKey(BuiltInTemplateKey.REMINDER.name())
                .build();

        Appointment appointment = Appointment.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .status(AppointmentStatus.CONFIRMED)
                .client(Client.builder().firstName("Anna").email("anna@test.com").build())
                .startTime(Instant.now().plusSeconds(86400))
                .build();

        when(emailTemplateRepository.findByTriggerTypeAndEnabledTrue(TriggerType.BEFORE_BOOKING))
                .thenReturn(List.of(template));
        when(emailTemplateRepository.findByTriggerTypeAndEnabledTrue(TriggerType.AFTER_BOOKING))
                .thenReturn(List.of());
        when(emailTemplateRepository.findByTriggerTypeAndEnabledTrue(TriggerType.CLIENT_BIRTHDAY))
                .thenReturn(List.of());
        when(emailTemplateRepository.findByTriggerTypeAndEnabledTrue(TriggerType.CLIENT_INACTIVE))
                .thenReturn(List.of());
        when(appointmentRepository.findByTenantIdAndDateRange(eq(tenantId), any(), any()))
                .thenReturn(List.of(appointment));

        triggerScheduler.processScheduledTriggers();

        verify(notificationDispatcher).enqueue(eq(TriggerType.BEFORE_BOOKING), any());
        verify(schedulerRunService).markRun(eq("TRIGGER_SCHEDULER"), any(Instant.class));
    }
}
