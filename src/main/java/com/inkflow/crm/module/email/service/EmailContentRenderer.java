package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.module.email.dto.EmailLayoutContext;
import com.inkflow.crm.module.email.dto.NotificationCommand;
import com.inkflow.crm.module.email.dto.RenderedEmail;
import com.inkflow.crm.module.email.enums.TemplateKey;
import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.template.EmailBodyHtmlConverter;
import com.inkflow.crm.module.email.template.EmailLayout;
import com.inkflow.crm.module.email.template.RenderedContent;
import com.inkflow.crm.module.email.template.EmailPreviewSampleData;
import com.inkflow.crm.module.email.template.TemplateResolver;
import com.inkflow.crm.module.email.template.TemplateVarSubstitutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailContentRenderer {

    private final TemplateResolver templateResolver;
    private final InkflowProperties inkflowProperties;

    public RenderedEmail render(NotificationCommand command) {
        return render(command.tenantId(), command.templateKey(), command.variables(), command.studioName());
    }

    public RenderedEmail render(UUID tenantId, TemplateKey templateKey, Map<String, String> variables,
                                String studioName) {
        RenderedContent template = templateResolver.resolve(tenantId, templateKey);
        Map<String, String> resolvedVariables = enrichVariables(variables, studioName);

        String subject = TemplateVarSubstitutor.substitute(template.subject(), resolvedVariables);
        String plainBody = TemplateVarSubstitutor.substitute(template.body(), resolvedVariables);
        String htmlBody = EmailBodyHtmlConverter.toHtml(plainBody);

        EmailLayoutContext layout = new EmailLayoutContext(
                inkflowProperties.getAppName(),
                subject,
                htmlBody,
                templateKey.getCategory(),
                studioName,
                resolvedVariables.get(TemplateVar.ACTION_URL.getPlaceholder()),
                null
        );

        return new RenderedEmail(subject, EmailLayout.wrap(layout));
    }

    public Map<String, String> sampleVariables(TemplateKey templateKey) {
        return EmailPreviewSampleData.forVars(
                templateKey.getAvailableVars(),
                inkflowProperties.getAppName(),
                null
        );
    }

    private Map<String, String> enrichVariables(Map<String, String> provided, String studioName) {
        Map<String, String> variables = new HashMap<>();

        if (provided != null) {
            variables.putAll(provided);
        }

        variables.put(TemplateVar.APP_NAME.getPlaceholder(), inkflowProperties.getAppName());
        variables.putIfAbsent(TemplateVar.STUDIO_NAME.getPlaceholder(), studioName != null ? studioName : "");

        return variables;
    }
}
