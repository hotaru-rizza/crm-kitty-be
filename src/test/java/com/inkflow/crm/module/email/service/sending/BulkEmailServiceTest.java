package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.Client;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.domain.enums.SupportedLocale;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.EmailComposeRequest;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.audit.service.AuditRecorder;
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
    @Mock private AuditRecorder auditRecorder;

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
    void renderPreview_substitutesSampleRecipientAndStudio() {
        String html = bulkEmailService.renderPreview(TENANT_ID,
                new EmailComposeRequest("Flash Day", "<p>Hi {client_name} from {studio_name}</p>", true));

        assertThat(html).contains("Олена");
        assertThat(html).contains("Ink Studio");
        assertThat(html).contains("CRM");
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

        when(clientRepository.findByIdInAndDeletedAtIsNull(List.of(withEmailId, withoutEmailId)))
                .thenReturn(List.of(withEmail, withoutEmail));

        SendEmailResultDto result = bulkEmailService.sendBulk(TENANT_ID,
                new SendEmailRequest(List.of(withEmailId, withoutEmailId), null, SUBJECT, BODY, false));

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

        when(staffRepository.findByIdInAndDeletedAtIsNull(List.of(staffId)))
                .thenReturn(List.of(staff));

        SendEmailResultDto result = bulkEmailService.sendBulk(TENANT_ID,
                new SendEmailRequest(null, List.of(staffId), SUBJECT, BODY, false));

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        verify(notificationDispatcher).enqueueManual(
                eq(TENANT_ID), eq(TriggerType.MANUAL), eq("artist@test.com"), eq(staff.getFullName()),
                eq(SUBJECT), anyString(), isNull());
        verifyNoInteractions(clientRepository);
    }

    @Test
    void sendBulk_substitutesClientNamePerRecipient() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Client clientOne = Client.builder()
                .id(id1).tenantId(TENANT_ID)
                .email("one@test.com").firstName("Anna").lastName("One").build();
        Client clientTwo = Client.builder()
                .id(id2).tenantId(TENANT_ID)
                .email("two@test.com").firstName("Bob").lastName("Two").build();

        when(clientRepository.findByIdInAndDeletedAtIsNull(List.of(id1, id2)))
                .thenReturn(List.of(clientOne, clientTwo));

        bulkEmailService.sendBulk(TENANT_ID,
                new SendEmailRequest(List.of(id1, id2), null, SUBJECT, "Привіт, {client_name}!", false));

        verify(notificationDispatcher).enqueueManual(
                eq(TENANT_ID), eq(TriggerType.MANUAL), eq("one@test.com"), eq(clientOne.getFullName()),
                eq(SUBJECT), argThat(html -> html.contains("Anna One")), isNull());
        verify(notificationDispatcher).enqueueManual(
                eq(TENANT_ID), eq(TriggerType.MANUAL), eq("two@test.com"), eq(clientTwo.getFullName()),
                eq(SUBJECT), argThat(html -> html.contains("Bob Two")), isNull());
    }
}
