package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.module.email.dto.EmailLayoutContext;
import com.inkflow.crm.module.email.dto.RenderedEmail;
import com.inkflow.crm.module.email.enums.TemplateCategory;
import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.template.EmailBodyHtmlConverter;
import com.inkflow.crm.module.email.template.EmailLayout;
import com.inkflow.crm.module.email.template.EmailPreviewSampleData;
import com.inkflow.crm.module.email.template.TemplateVarSubstitutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class TemplateEmailRenderer {

    private final InkflowProperties inkflowProperties;

    public RenderedEmail render(EmailTemplate template, Map<String, String> variables,
                                String studioName, String studioLogoUrl) {
        Map<String, String> resolvedVariables = enrichVariables(variables, studioName, template.getTriggerType());

        return renderContent(
                template.getSubject(),
                template.getBody(),
                resolvedVariables,
                template.getCategory(),
                studioName,
                studioLogoUrl,
                resolvedVariables.get(TemplateVar.ACTION_URL.getPlaceholder())
        );
    }

    public RenderedEmail renderDraft(TriggerType triggerType, String subject, String body,
                                     String studioName, String studioLogoUrl) {
        Map<String, String> resolvedVariables = enrichVariables(
                sampleVariables(triggerType, studioName),
                studioName,
                triggerType
        );

        return renderContent(
                subject,
                body,
                resolvedVariables,
                triggerType.getCategory(),
                studioName,
                studioLogoUrl,
                resolvedVariables.get(TemplateVar.ACTION_URL.getPlaceholder())
        );
    }

    private RenderedEmail renderContent(
            String subject,
            String body,
            Map<String, String> resolvedVariables,
            TemplateCategory category,
            String studioName,
            String studioLogoUrl,
            String actionUrl) {

        String resolvedSubject = TemplateVarSubstitutor.substitute(subject, resolvedVariables);
        String resolvedBody = TemplateVarSubstitutor.substitute(body, resolvedVariables);
        String htmlBody = EmailBodyHtmlConverter.toHtml(resolvedBody);

        EmailLayoutContext layout = new EmailLayoutContext(
                inkflowProperties.getAppName(),
                resolvedSubject,
                htmlBody,
                category,
                studioName,
                studioLogoUrl,
                actionUrl,
                null
        );

        return new RenderedEmail(resolvedSubject, EmailLayout.wrap(layout));
    }

    public Map<String, String> sampleVariables(TriggerType triggerType) {
        return sampleVariables(triggerType, null);
    }

    public Map<String, String> sampleVariables(TriggerType triggerType, String studioName) {
        return EmailPreviewSampleData.forTrigger(
                triggerType,
                inkflowProperties.getAppName(),
                studioName
        );
    }

    private Map<String, String> enrichVariables(
            Map<String, String> provided,
            String studioName,
            TriggerType triggerType) {

        Map<String, String> variables = new java.util.HashMap<>();
        if (provided != null) {
            variables.putAll(provided);
        }

        variables.put(TemplateVar.APP_NAME.getPlaceholder(), inkflowProperties.getAppName());
        variables.putIfAbsent(TemplateVar.STUDIO_NAME.getPlaceholder(), studioName != null ? studioName : "");

        for (TemplateVar variable : triggerType.getProvidedVars()) {
            variables.putIfAbsent(variable.getPlaceholder(), "");
        }

        return variables;
    }
}
