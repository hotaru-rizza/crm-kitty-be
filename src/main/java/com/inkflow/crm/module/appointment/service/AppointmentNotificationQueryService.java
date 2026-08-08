package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.domain.entity.EmailMessage;
import com.inkflow.crm.domain.repository.EmailMessageRepository;
import com.inkflow.crm.module.appointment.dto.AppointmentNotificationDto;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppointmentNotificationQueryService {

    private final EmailMessageRepository emailMessageRepository;
    private final AppointmentEntityResolver entityResolver;

    @Transactional(readOnly = true)
    public List<AppointmentNotificationDto> getNotifications(UUID appointmentId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        entityResolver.requireAppointment(tenantId, appointmentId);

        return emailMessageRepository
                .findByEntityIdOrderByCreatedAtDesc(appointmentId)
                .stream()
                .filter(this::isClientAppointmentNotification)
                .map(this::toDto)
                .toList();
    }

    private boolean isClientAppointmentNotification(EmailMessage message) {
        TriggerType triggerType = message.getTriggerType();
        return triggerType != null && triggerType.isClientAppointmentNotification();
    }

    private AppointmentNotificationDto toDto(EmailMessage message) {
        TriggerType triggerType = message.getTriggerType();
        return AppointmentNotificationDto.builder()
                .triggerType(triggerType != null ? triggerType.name() : null)
                .status(message.getStatus() != null ? message.getStatus().name() : null)
                .sentAt(message.getSentAt())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
