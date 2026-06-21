package com.inkflow.crm.module.project.service;

import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.module.appointment.event.AppointmentCanceledEvent;
import com.inkflow.crm.module.appointment.event.AppointmentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectProgressSyncListener {

    private final AppointmentRepository appointmentRepository;
    private final ProjectProgressSyncService projectProgressSyncService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAppointmentCompleted(AppointmentCompletedEvent event) {
        syncLinkedProject(event.appointmentId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onAppointmentCanceled(AppointmentCanceledEvent event) {
        syncLinkedProject(event.appointmentId());
    }

    private void syncLinkedProject(java.util.UUID appointmentId) {
        appointmentRepository.findById(appointmentId)
                .ifPresent(appointment -> {
                    if (appointment.getProject() == null) {
                        return;
                    }
                    projectProgressSyncService.syncProject(appointment.getProject().getId());
                });
    }
}
