package com.inkflow.crm.module.email.template;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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


}
