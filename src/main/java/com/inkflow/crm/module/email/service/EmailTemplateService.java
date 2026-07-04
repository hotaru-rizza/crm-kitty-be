package com.inkflow.crm.module.email.service;

import com.inkflow.crm.common.exception.BusinessRuleException;
import com.inkflow.crm.common.exception.ErrorCode;
import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.domain.repository.EmailTemplateRepository;
import com.inkflow.crm.domain.enums.AuditAction;
import com.inkflow.crm.domain.enums.AuditEntityType;
import com.inkflow.crm.module.audit.annotation.Audited;
import com.inkflow.crm.module.audit.service.AuditRecorder;
import com.inkflow.crm.module.audit.support.AuditLabelFormatter;
import com.inkflow.crm.module.email.dto.CreateEmailTemplateRequest;
import com.inkflow.crm.module.email.dto.EmailTemplatePreviewRequest;
import com.inkflow.crm.module.email.dto.EmailTemplateResponseDto;
import com.inkflow.crm.module.email.dto.EmailTenantContext;
import com.inkflow.crm.module.email.dto.UpdateEmailTemplateRequest;
import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.enums.TriggerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailTemplateService {

    private final EmailTemplateRepository emailTemplateRepository;
    private final TemplateEmailRenderer templateEmailRenderer;
    private final EmailTenantContextLoader tenantContextLoader;
    private final AuditRecorder auditRecorder;
    private final AuditLabelFormatter auditLabelFormatter;

    @Transactional(readOnly = true)
    public List<EmailTemplateResponseDto> list(UUID tenantId) {
        return emailTemplateRepository.findAllByOrderByCategoryAscTriggerTypeAscBuiltinKeyAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    @Audited(
            action = AuditAction.CREATE,
            entityType = AuditEntityType.EMAIL_TEMPLATE,
            entityId = "#result.id",
            entityLabel = "#result.subject"
    )
    public EmailTemplateResponseDto create(UUID tenantId, CreateEmailTemplateRequest request, UUID createdBy) {
        validateOffset(request.triggerType(), request.offsetMinutes());

        EmailTemplate template = EmailTemplate.builder()
                .tenantId(tenantId)
                .triggerType(request.triggerType())
                .offsetMinutes(request.offsetMinutes())
                .subject(request.subject())
                .body(request.body())
                .enabled(request.enabled() != null ? request.enabled() : true)
                .deletable(true)
                .category(request.triggerType().getCategory())
                .updatedBy(createdBy)
                .build();

        EmailTemplate saved = emailTemplateRepository.save(template);
        log.info("Email template created: tenantId={} id={}", tenantId, saved.getId());
        return toDto(saved);
    }

    @Transactional
    @Audited(
            action = AuditAction.UPDATE,
            entityType = AuditEntityType.EMAIL_TEMPLATE,
            entityId = "#templateId.toString()",
            entityLabel = "#result.subject"
    )
    public EmailTemplateResponseDto update(
            UUID tenantId,
            UUID templateId,
            UpdateEmailTemplateRequest request,
            UUID updatedBy) {

        EmailTemplate template = requireTemplate(tenantId, templateId);

        if (request.subject() != null) {
            template.setSubject(request.subject());
        }
        if (request.body() != null) {
            template.setBody(request.body());
        }
        if (request.enabled() != null) {
            template.setEnabled(request.enabled());
        }
        if (request.offsetMinutes() != null) {
            validateOffset(template.getTriggerType(), request.offsetMinutes());
            template.setOffsetMinutes(request.offsetMinutes());
        }
        if (request.triggerType() != null && template.getBuiltinKey() == null) {
            validateOffset(request.triggerType(), request.offsetMinutes() != null
                    ? request.offsetMinutes()
                    : template.getOffsetMinutes());
            template.setTriggerType(request.triggerType());
            template.setCategory(request.triggerType().getCategory());
        }

        template.setUpdatedBy(updatedBy);
        EmailTemplate saved = emailTemplateRepository.save(template);
        log.info("Email template updated: tenantId={} id={}", tenantId, saved.getId());
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID tenantId, UUID templateId) {
        EmailTemplate template = requireTemplate(tenantId, templateId);

        if (Boolean.FALSE.equals(template.getDeletable())) {
            throw new BusinessRuleException(ErrorCode.FORBIDDEN, "Built-in template cannot be deleted");
        }

        String label = auditLabelFormatter.emailTemplate(template.getSubject());
        emailTemplateRepository.delete(template);
        log.info("Email template deleted: tenantId={} id={}", tenantId, templateId);
        auditRecorder.record(
                AuditAction.DELETE,
                AuditEntityType.EMAIL_TEMPLATE,
                templateId.toString(),
                label
        );
    }

    @Transactional(readOnly = true)
    public String preview(UUID tenantId, UUID templateId) {
        EmailTemplate template = requireTemplate(tenantId, templateId);
        String studioName = previewStudioName(tenantId);

        return templateEmailRenderer.render(
                template,
                templateEmailRenderer.sampleVariables(template.getTriggerType(), studioName),
                studioName
        ).html();
    }

    @Transactional(readOnly = true)
    public String previewDraft(UUID tenantId, EmailTemplatePreviewRequest request) {
        String studioName = previewStudioName(tenantId);

        return templateEmailRenderer.renderDraft(
                request.triggerType(),
                request.subject(),
                request.body(),
                studioName
        ).html();
    }

    private String previewStudioName(UUID tenantId) {
        EmailTenantContext context = tenantContextLoader.loadContext(tenantId);
        return context.studioName();
    }

    @Transactional(readOnly = true)
    public List<TriggerTypeInfo> listTriggerTypes() {
        return Arrays.stream(TriggerType.values())
                .map(type -> new TriggerTypeInfo(
                        type.name(),
                        type.getCategory(),
                        type.isScheduled(),
                        type.isEventDriven(),
                        type.isRequiresOffset(),
                        type.getProvidedVars().stream().map(TemplateVar::getPlaceholder).sorted().toList()))
                .toList();
    }

    private EmailTemplate requireTemplate(UUID tenantId, UUID templateId) {
        return emailTemplateRepository.findByIdAndTenantId(templateId, tenantId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.NOT_FOUND, "Email template not found"));
    }

    private void validateOffset(TriggerType triggerType, Integer offsetMinutes) {
        if (triggerType.isRequiresOffset() && (offsetMinutes == null || offsetMinutes <= 0)) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Trigger " + triggerType + " requires positive offset_minutes");
        }
        if (!triggerType.isRequiresOffset() && offsetMinutes != null) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_ERROR,
                    "Trigger " + triggerType + " does not support offset_minutes");
        }
    }

    private EmailTemplateResponseDto toDto(EmailTemplate template) {
        return EmailTemplateResponseDto.builder()
                .id(template.getId())
                .triggerType(template.getTriggerType())
                .offsetMinutes(template.getOffsetMinutes())
                .subject(template.getSubject())
                .body(template.getBody())
                .enabled(template.getEnabled())
                .deletable(template.getDeletable())
                .builtinKey(template.getBuiltinKey())
                .category(template.getCategory())
                .availableVars(template.getTriggerType().getProvidedVars().stream()
                        .map(TemplateVar::getPlaceholder)
                        .sorted()
                        .toList())
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    public record TriggerTypeInfo(
            String type,
            com.inkflow.crm.module.email.enums.TemplateCategory category,
            boolean scheduled,
            boolean eventDriven,
            boolean requiresOffset,
            List<String> availableVars
    ) {}
}
