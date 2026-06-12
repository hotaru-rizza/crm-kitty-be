package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.enums.TemplateVar;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TemplateVars {

    private final Map<String, String> values = new LinkedHashMap<>();

    public TemplateVars put(TemplateVar variable, String value) {
        values.put(variable.getPlaceholder(), value);
        return this;
    }

    public Map<String, String> toMap() {
        return Map.copyOf(values);
    }
}
