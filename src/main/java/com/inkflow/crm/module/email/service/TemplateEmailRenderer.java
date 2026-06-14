package com.inkflow.crm.module.email.service;

import com.inkflow.crm.config.InkflowProperties;
import com.inkflow.crm.domain.entity.EmailTemplate;
import com.inkflow.crm.module.email.dto.EmailLayoutContext;
import com.inkflow.crm.module.email.dto.RenderedEmail;
import com.inkflow.crm.module.email.enums.TemplateCategory;
import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.enums.TriggerType;
import com.inkflow.crm.module.email.template.EmailLayout;
import com.inkflow.crm.module.email.template.TemplateVarSubstitutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TemplateEmailRenderer {

    private final InkflowProperties inkflowProperties;

    public RenderedEmail render(EmailTemplate template, Map<String, String> variables, String studioName) {
        Map<String, String> resolvedVariables = enrichVariables(variables, studioName, template.getTriggerType());

        String subject = TemplateVarSubstitutor.substitute(template.getSubject(), resolvedVariables);
        String plainBody = TemplateVarSubstitutor.substitute(template.getBody(), resolvedVariables);
        String htmlBody = EmailLayout.toHtml(plainBody);

        EmailLayoutContext layout = new EmailLayoutContext(
                inkflowProperties.getAppName(),
                subject,
                htmlBody,
                template.getCategory(),
                studioName,
                resolvedVariables.get(TemplateVar.ACTION_URL.getPlaceholder()),
                null
        );

        return new RenderedEmail(subject, EmailLayout.wrap(layout));
    }

    public Map<String, String> sampleVariables(TriggerType triggerType) {
        Map<String, String> variables = new HashMap<>();
        Set<TemplateVar> available = triggerType.getProvidedVars();

        for (TemplateVar variable : available) {
            variables.put(variable.getPlaceholder(), "[" + variable.getPlaceholder() + "]");
        }

        variables.put(TemplateVar.APP_NAME.getPlaceholder(), inkflowProperties.getAppName());
        variables.put(TemplateVar.STUDIO_NAME.getPlaceholder(), "[studio_name]");
        return variables;
    }

    private Map<String, String> enrichVariables(
            Map<String, String> provided,
            String studioName,
            TriggerType triggerType) {

        Map<String, String> variables = new HashMap<>();
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
