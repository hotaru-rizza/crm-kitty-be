package com.inkflow.crm.module.email.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.appointment.event.AppointmentCanceledEvent;
import com.inkflow.crm.module.appointment.event.AppointmentCompletedEvent;
import com.inkflow.crm.module.appointment.event.AppointmentConfirmedEvent;
import com.inkflow.crm.module.appointment.event.AppointmentRescheduledEvent;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.NotificationDispatchContext;
import com.inkflow.crm.module.email.enums.TriggerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentEmailListener {

    private final AppointmentRepository appointmentRepository;
    private final EmailTenantContextLoader tenantContextLoader;
    private final TriggerVariableBuilder variableBuilder;
    private final NotificationDispatcher notificationDispatcher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onConfirmed(AppointmentConfirmedEvent event) {
        dispatchForAppointment(event.appointmentId(), TriggerType.BOOKING_CONFIRMED, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCanceled(AppointmentCanceledEvent event) {
        dispatchForAppointment(event.appointmentId(), TriggerType.BOOKING_CANCELED, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onRescheduled(AppointmentRescheduledEvent event) {
        dispatchForAppointment(event.appointmentId(), TriggerType.BOOKING_RESCHEDULED, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onCompleted(AppointmentCompletedEvent event) {
        dispatchForAppointment(event.appointmentId(), TriggerType.BOOKING_COMPLETED, null);
    }

    private void dispatchForAppointment(
            java.util.UUID appointmentId,
            TriggerType triggerType,
            Integer offsetMinutes) {

        appointmentRepository.findById(appointmentId).ifPresent(appointment -> {
            if (clientEmailMissing(appointment)) {
                return;
            }

            EmailTenantContext tenantContext = tenantContextLoader.loadContext(appointment.getTenantId());
            NotificationDispatchContext context = variableBuilder.forClient(
                    appointment, tenantContext, triggerType, offsetMinutes);

            int enqueued = notificationDispatcher.enqueue(triggerType, context);
            log.debug("Enqueued {} messages for trigger {} appointment {}", enqueued, triggerType, appointmentId);
        });
    }

    private boolean clientEmailMissing(Appointment appointment) {
        String email = appointment.getClient().getEmail();
        return email == null || email.isBlank();
    }
}
