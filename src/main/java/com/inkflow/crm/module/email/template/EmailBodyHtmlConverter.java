package com.inkflow.crm.module.email.template;

public final class EmailBodyHtmlConverter {

    private EmailBodyHtmlConverter() {
    }

    public static String toHtml(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        if (containsHtmlMarkup(body)) {
            return EmailHtmlSanitizer.sanitize(body);
        }

        return EmailLayout.toHtml(body);
    }

    private static boolean containsHtmlMarkup(String body) {
        int open = body.indexOf('<');
        int close = body.indexOf('>');
        return open >= 0 && close > open;
    }
}
