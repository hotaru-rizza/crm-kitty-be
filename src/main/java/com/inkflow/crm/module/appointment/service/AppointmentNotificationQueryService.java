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
                .findByEntityIdOrderByCreatedAtDesc( appointmentId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    private AppointmentNotificationDto toDto(EmailMessage message) {
        TriggerType triggerType = message.getTriggerType();
        return AppointmentNotificationDto.builder()
                .triggerType(triggerType != null ? triggerType.name() : null)
                .triggerLabel(resolveTriggerLabel(triggerType))
                .status(message.getStatus() != null ? message.getStatus().name() : null)
                .sentAt(message.getSentAt())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private String resolveTriggerLabel(TriggerType triggerType) {
        if (triggerType == null) {
            return null;
        }
        return switch (triggerType) {
            case BOOKING_CONFIRMED -> "Appointment confirmation";
            case BEFORE_BOOKING -> "Appointment reminder";
            case AFTER_BOOKING -> "Repeat appointment reminder";
            case BOOKING_COMPLETED -> "Review request";
            case BOOKING_CANCELED -> "Cancellation notice";
            case BOOKING_RESCHEDULED -> "Reschedule notice";
            default -> triggerType.name();
        };
    }
}
