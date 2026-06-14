package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.email.dto.EmailLayoutContext;
import com.inkflow.crm.module.email.dto.EmailRecipient;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.enums.TemplateCategory;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.service.EmailTenantContextLoader;
import com.inkflow.crm.module.email.service.NotificationDispatcher;
import com.inkflow.crm.module.email.template.EmailLayout;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkEmailService {

    private final NotificationDispatcher notificationDispatcher;
    private final ClientRepository clientRepository;
    private final StaffRepository staffRepository;
    private final EmailTenantContextLoader tenantContextLoader;
    private final InkflowProperties inkflowProperties;

    @Transactional
    public SendEmailResultDto sendBulk(UUID tenantId, SendEmailRequest request) {
        List<EmailRecipient> recipients = resolveRecipients(tenantId, request);
        int requested = request.clientIds().size() + request.staffIds().size();
        EmailTenantContext context = tenantContextLoader.loadContext(tenantId);
        String htmlBody = wrapManualBody(request.subject(), request.body(), context.studioName());

        recipients.forEach(recipient -> notificationDispatcher.enqueueManual(
                tenantId,
                TriggerType.MANUAL,
                recipient.email(),
                recipient.name(),
                request.subject(),
                htmlBody,
                null));

        int sent = recipients.size();
        int skipped = requested - sent;
        log.info("Bulk email: tenantId={} queued={} skipped={}", tenantId, sent, skipped);

        return new SendEmailResultDto(sent, skipped);
    }

    private String wrapManualBody(String subject, String textBody, String studioName) {
        String bodyHtml = EmailLayout.toHtml(textBody);
        EmailLayoutContext layout = new EmailLayoutContext(
                inkflowProperties.getAppName(),
                subject,
                bodyHtml,
                TemplateCategory.MARKETING,
                studioName,
                null,
                null
        );
        return EmailLayout.wrap(layout);
    }

    private List<EmailRecipient> resolveRecipients(UUID tenantId, SendEmailRequest request) {
        List<EmailRecipient> recipients = new ArrayList<>();
        recipients.addAll(resolveClients(tenantId, request.clientIds()));
        recipients.addAll(resolveStaff(tenantId, request.staffIds()));
        return recipients;
    }

    private List<EmailRecipient> resolveClients(UUID tenantId, List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return clientRepository.findByIdInAndTenantIdAndDeletedAtIsNull(ids, tenantId).stream()
                .filter(client -> hasEmail(client.getEmail()))
                .map(client -> EmailRecipient.of(client.getEmail(), client.getFullName()))
                .toList();
    }

    private List<EmailRecipient> resolveStaff(UUID tenantId, List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return staffRepository.findByIdInAndTenantIdAndDeletedAtIsNull(ids, tenantId).stream()
                .filter(staff -> hasEmail(staff.getEmail()))
                .map(staff -> EmailRecipient.of(staff.getEmail(), staff.getFullName()))
                .toList();
    }

    private boolean hasEmail(String email) {
        return email != null && !email.isBlank();
    }
}
