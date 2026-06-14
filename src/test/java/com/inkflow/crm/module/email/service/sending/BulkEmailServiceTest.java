package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.enums.SupportedLocale;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.service.EmailTenantContextLoader;
import com.inkflow.crm.module.email.service.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkEmailServiceTest {

    @Mock private NotificationDispatcher notificationDispatcher;
    @Mock private ClientRepository clientRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private EmailTenantContextLoader tenantContextLoader;
    @Mock private InkflowProperties inkflowProperties;

    @InjectMocks
    private BulkEmailService bulkEmailService;

    private static final String SUBJECT = "Hello";
    private static final String BODY = "Body";
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(inkflowProperties.getAppName()).thenReturn("CRM");
        when(tenantContextLoader.loadContext(TENANT_ID))
                .thenReturn(new EmailTenantContext("Ink Studio", "Europe/Kyiv", SupportedLocale.UK));
    }

    @Test
    void sendBulk_skipsClientsWithoutEmail() {
        UUID withEmailId = UUID.randomUUID();
        UUID withoutEmailId = UUID.randomUUID();

        Client withEmail = Client.builder()
                .id(withEmailId).tenantId(TENANT_ID)
                .email("client@test.com").firstName("Anna").lastName("Ink").build();
        Client withoutEmail = Client.builder()
                .id(withoutEmailId).tenantId(TENANT_ID)
                .firstName("Bob").lastName("Ink").build();

        when(clientRepository.findByIdInAndTenantIdAndDeletedAtIsNull(
                List.of(withEmailId, withoutEmailId), TENANT_ID))
                .thenReturn(List.of(withEmail, withoutEmail));

        SendEmailResultDto result = bulkEmailService.sendBulk(TENANT_ID,
                new SendEmailRequest(List.of(withEmailId, withoutEmailId), null, SUBJECT, BODY));

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        verify(notificationDispatcher).enqueueManual(
                eq(TENANT_ID), eq(TriggerType.MANUAL), eq("client@test.com"), eq(withEmail.getFullName()),
                eq(SUBJECT), anyString(), isNull());
        verifyNoInteractions(staffRepository);
    }

    @Test
    void sendBulk_sendsToStaff() {
        UUID staffId = UUID.randomUUID();

        Staff staff = Staff.builder().id(staffId).tenantId(TENANT_ID)
                .email("artist@test.com").firstName("Alex").lastName("Tat").build();

        when(staffRepository.findByIdInAndTenantIdAndDeletedAtIsNull(List.of(staffId), TENANT_ID))
                .thenReturn(List.of(staff));

        SendEmailResultDto result = bulkEmailService.sendBulk(TENANT_ID,
                new SendEmailRequest(null, List.of(staffId), SUBJECT, BODY));

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        verify(notificationDispatcher).enqueueManual(
                eq(TENANT_ID), eq(TriggerType.MANUAL), eq("artist@test.com"), eq(staff.getFullName()),
                eq(SUBJECT), anyString(), isNull());
        verifyNoInteractions(clientRepository);
    }
}
