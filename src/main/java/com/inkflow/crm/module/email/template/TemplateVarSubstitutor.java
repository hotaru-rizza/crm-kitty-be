package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.enums.TemplateKey;
import com.inkflow.crm.module.email.enums.TemplateVar;

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TemplateVarSubstitutor {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");

    private TemplateVarSubstitutor() {}

    public static String substitute(String text, Map<String, String> vars) {
        if (text == null || text.isBlank() || vars == null || vars.isEmpty()) {
            return text == null ? "" : text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = vars.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static Set<String> missingVars(String text, TemplateKey key) {
        if (text == null) {
            return key.getRequiredVars().stream()
                    .map(TemplateVar::getPlaceholder)
                    .collect(Collectors.toSet());
        }
        return key.getRequiredVars().stream()
                .map(TemplateVar::getPlaceholder)
                .filter(p -> !text.contains("{" + p + "}"))
                .collect(Collectors.toSet());
    }
}
