package com.inkflow.crm.module.project.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.Project;
import com.inkflow.crm.domain.enums.AppointmentStatus;
import com.inkflow.crm.domain.repository.AppointmentRepository;
import com.inkflow.crm.domain.repository.ProjectRepository;
import com.inkflow.crm.module.payment.dto.AppointmentPaymentSummaryDto;
import com.inkflow.crm.module.payment.support.AppointmentPaymentSummaryCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectProgressSyncService {

    private final ProjectRepository projectRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentPaymentSummaryCalculator paymentSummaryCalculator;

    @Transactional
    public void syncProject(UUID projectId) {
        if (projectId == null) {
            return;
        }

        projectRepository.findByIdAndDeletedAtIsNull(projectId)
                .ifPresent(this::syncProjectState);
    }

    @Transactional
    public void syncForAppointment(UUID appointmentId) {
        if (appointmentId == null) {
            return;
        }

        appointmentRepository.findByIdAndDeletedAtIsNull(appointmentId)
                .map(Appointment::getProject)
                .ifPresent(project -> syncProject(project.getId()));
    }

    private void syncProjectState(Project project) {
        List<Appointment> appointments = appointmentRepository.findByProjectIdAndDeletedAtIsNull(project.getId());

        int completedSessions = (int) appointments.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.COMPLETED)
                .count();

        BigDecimal totalPaid = appointments.stream()
                .map(paymentSummaryCalculator::calculate)
                .map(AppointmentPaymentSummaryDto::getTotalPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        project.setCompletedSessions(completedSessions);
        project.setTotalPaid(totalPaid);
        projectRepository.save(project);

        log.info("Project progress synced: tenantId={} projectId={} completedSessions={} totalPaid={}",
                project.getTenantId(), project.getId(), completedSessions, totalPaid);
    }
}
