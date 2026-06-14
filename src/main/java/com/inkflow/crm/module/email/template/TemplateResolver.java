package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.enums.TemplateKey;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TemplateResolver {

    public RenderedContent resolve(UUID tenantId, TemplateKey key) {
        return TemplateDefaults.get(key);
    }
}
