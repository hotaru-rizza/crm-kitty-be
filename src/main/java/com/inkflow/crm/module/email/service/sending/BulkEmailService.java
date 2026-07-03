package com.inkflow.crm.module.email.service.sending;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.domain.repository.ClientRepository;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.domain.repository.StaffRepository;
import com.inkflow.crm.module.email.dto.EmailComposeRequest;
import com.inkflow.crm.module.email.dto.EmailLayoutContext;
import com.inkflow.crm.module.email.dto.EmailRecipient;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.SendEmailRequest;
import com.inkflow.crm.module.email.dto.SendEmailResultDto;
import com.inkflow.crm.module.email.enums.TemplateCategory;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.service.EmailTenantContextLoader;
import com.inkflow.crm.module.email.service.NotificationDispatcher;
import com.inkflow.crm.module.email.template.EmailHtmlSanitizer;
import com.inkflow.crm.module.email.template.EmailLayout;
import com.inkflow.crm.module.email.template.EmailPreviewSampleData;
import com.inkflow.crm.module.email.template.TemplateVarSubstitutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkEmailService {

    private static final String PREVIEW_CLIENT_NAME = EmailPreviewSampleData.SAMPLE_CLIENT_NAME;

    private final NotificationDispatcher notificationDispatcher;
    private final ClientRepository clientRepository;
    private final StaffRepository staffRepository;
    private final EmailTenantContextLoader tenantContextLoader;
    private final InkflowProperties inkflowProperties;
    private final AuditRecorder auditRecorder;

    @Transactional
    public SendEmailResultDto sendBulk(UUID tenantId, SendEmailRequest request) {
        List<EmailRecipient> recipients = resolveRecipients(tenantId, request);
        int requested = request.clientIds().size() + request.staffIds().size();
        EmailTenantContext context = tenantContextLoader.loadContext(tenantId);
        String rawBody = prepareRawBody(request.body(), request.html());

        recipients.forEach(recipient -> {
            RenderedManualEmail rendered = renderForRecipient(
                    request.subject(),
                    rawBody,
                    request.html(),
                    recipient,
                    context);
            notificationDispatcher.enqueueManual(
                    tenantId,
                    TriggerType.MANUAL,
                    recipient.email(),
                    recipient.name(),
                    rendered.subject(),
                    rendered.fullHtml(),
                    null);
        });

        int sent = recipients.size();
        int skipped = requested - sent;
        log.info("Bulk email: tenantId={} queued={} skipped={}", tenantId, sent, skipped);
        auditRecorder.record(
                AuditAction.UPDATE,
                AuditEntityType.TENANT,
                tenantId.toString(),
                context.studioName(),
                null,
                "Розсилка: " + sent + " отримувачів · " + request.subject()
        );

        return new SendEmailResultDto(sent, skipped);
    }

    public String renderPreview(UUID tenantId, EmailComposeRequest request) {
        EmailTenantContext context = tenantContextLoader.loadContext(tenantId);
        EmailRecipient sampleRecipient = EmailRecipient.of("preview@example.com", PREVIEW_CLIENT_NAME);
        String rawBody = prepareRawBody(request.body(), request.html());

        return renderForRecipient(request.subject(), rawBody, request.html(), sampleRecipient, context).fullHtml();
    }

    @Transactional
    public void sendTest(UUID tenantId, String recipientEmail, String recipientName, EmailComposeRequest request) {
        EmailTenantContext context = tenantContextLoader.loadContext(tenantId);
        EmailRecipient recipient = EmailRecipient.of(recipientEmail, recipientName);
        String rawBody = prepareRawBody(request.body(), request.html());
        RenderedManualEmail rendered = renderForRecipient(
                request.subject(),
                rawBody,
                request.html(),
                recipient,
                context);

        notificationDispatcher.enqueueManual(
                tenantId,
                TriggerType.MANUAL,
                recipient.email(),
                recipient.name(),
                rendered.subject(),
                rendered.fullHtml(),
                null);

        log.info("Test email queued: tenantId={} recipient={}", tenantId, recipientEmail);
    }

    private String prepareRawBody(String body, boolean html) {
        return html ? EmailHtmlSanitizer.sanitize(body) : body;
    }

    private RenderedManualEmail renderForRecipient(
            String subjectTemplate,
            String rawBody,
            boolean html,
            EmailRecipient recipient,
            EmailTenantContext context) {

        Map<String, String> vars = buildVars(recipient, context);
        String subject = TemplateVarSubstitutor.substitute(subjectTemplate, vars);
        String bodyResolved = TemplateVarSubstitutor.substitute(rawBody, vars);
        String bodyHtml = html ? bodyResolved : EmailLayout.toHtml(bodyResolved);
        String fullHtml = wrapBody(subject, bodyHtml, context.studioName());

        return new RenderedManualEmail(subject, fullHtml);
    }

    private Map<String, String> buildVars(EmailRecipient recipient, EmailTenantContext context) {
        return EmailPreviewSampleData.forManualCompose(
                recipient.name(),
                context.studioName(),
                inkflowProperties.getAppName()
        );
    }

    private String wrapBody(String subject, String bodyHtml, String studioName) {
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

        return clientRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .filter(client -> hasEmail(client.getEmail()))
                .map(client -> EmailRecipient.of(client.getEmail(), client.getFullName()))
                .toList();
    }

    private List<EmailRecipient> resolveStaff(UUID tenantId, List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return staffRepository.findByIdInAndDeletedAtIsNull(ids).stream()
                .filter(staff -> hasEmail(staff.getEmail()))
                .map(staff -> EmailRecipient.of(staff.getEmail(), staff.getFullName()))
                .toList();
    }

    private boolean hasEmail(String email) {
        return email != null && !email.isBlank();
    }

    private record RenderedManualEmail(String subject, String fullHtml) {}
}
