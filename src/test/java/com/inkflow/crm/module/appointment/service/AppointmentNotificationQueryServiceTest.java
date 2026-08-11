package com.inkflow.crm.module.appointment.service;

import com.inkflow.crm.domain.entity.Appointment;
import com.inkflow.crm.domain.entity.EmailMessage;
import com.inkflow.crm.domain.enums.EmailMessageStatus;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.EmailMessageRepository;
import com.inkflow.crm.module.appointment.dto.AppointmentNotificationDto;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.support.SecurityTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentNotificationQueryServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID APPOINTMENT_ID = UUID.randomUUID();

    @Mock
    private EmailMessageRepository emailMessageRepository;

    @Mock
    private AppointmentEntityResolver entityResolver;

    @InjectMocks
    private AppointmentNotificationQueryService service;

    @BeforeEach
    void setUp() {
        SecurityTestSupport.authenticate(UUID.randomUUID(), TENANT_ID, UserRole.OWNER);
    }

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clearAuthentication();
    }

    @Test
    void getNotifications_returnsOnlyClientAppointmentEmails() {
        when(entityResolver.requireAppointment(TENANT_ID, APPOINTMENT_ID)).thenReturn(mock(Appointment.class));
        when(emailMessageRepository.findByEntityIdOrderByCreatedAtDesc(APPOINTMENT_ID)).thenReturn(List.of(
                email(TriggerType.BOOKING_CONFIRMED),
                email(TriggerType.MANUAL),
                email(TriggerType.STAFF_APPOINTMENT),
                email(TriggerType.BEFORE_BOOKING)
        ));

        List<AppointmentNotificationDto> result = service.getNotifications(APPOINTMENT_ID);

        assertThat(result).extracting(AppointmentNotificationDto::getTriggerType)
                .containsExactly(TriggerType.BOOKING_CONFIRMED.name(), TriggerType.BEFORE_BOOKING.name());
        verify(entityResolver).requireAppointment(TENANT_ID, APPOINTMENT_ID);
    }

    private static EmailMessage email(TriggerType triggerType) {
        return EmailMessage.builder()
                .id(UUID.randomUUID())
                .tenantId(TENANT_ID)
                .triggerType(triggerType)
                .recipientEmail("client@test.com")
                .subject("Subject")
                .body("<html/>")
                .status(EmailMessageStatus.SENT)
                .attempts(0)
                .entityId(APPOINTMENT_ID)
                .createdAt(Instant.parse("2026-08-05T00:26:00Z"))
                .sentAt(Instant.parse("2026-08-05T00:26:00Z"))
                .build();
    }
}
