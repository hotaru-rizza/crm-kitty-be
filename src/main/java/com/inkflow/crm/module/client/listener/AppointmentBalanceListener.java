package com.inkflow.crm.module.client.listener;

import com.inkflow.crm.module.appointment.event.AppointmentCompletedEvent;
import com.inkflow.crm.module.appointment.event.AppointmentRestoredEvent;
import com.inkflow.crm.module.client.service.ClientBalanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentBalanceListener {

    private final ClientBalanceService clientBalanceService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAppointmentCompleted(AppointmentCompletedEvent event) {
        try {
            clientBalanceService.chargeAppointmentOnCompletion(event.appointmentId(), event.tenantId());
        } catch (Exception exception) {
            log.warn("Failed to charge client balance on completion appointmentId={}: {}",
                    event.appointmentId(), exception.getMessage());
            throw exception;
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAppointmentRestored(AppointmentRestoredEvent event) {
        try {
            clientBalanceService.reverseAppointmentCharge(event.appointmentId(), event.tenantId());
        } catch (Exception exception) {
            log.warn("Failed to reverse client balance charge appointmentId={}: {}",
                    event.appointmentId(), exception.getMessage());
            throw exception;
        }
    }
}
