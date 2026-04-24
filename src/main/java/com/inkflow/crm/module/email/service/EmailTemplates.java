package com.inkflow.crm.module.email.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EmailTemplates {

    private EmailTemplates() {}

    private static final String BRAND_COLOR = "#c026d3";
    private static final String BG_COLOR = "#1a1b1e";
    private static final String CARD_BG = "#25262b";
    private static final String TEXT_COLOR = "#c1c2c5";
    private static final String TEXT_BRIGHT = "#ffffff";

    private static final String DEFAULT_CONFIRMATION_SUBJECT = "Запис підтверджено — {{studio}}";
    private static final String DEFAULT_CONFIRMATION_BODY =
            "Привіт, {{client_name}}! Ваш запис підтверджено.\n\n" +
            "Якщо потрібно скасувати або перенести — зв'яжіться з нами завчасно.";

    private static final String DEFAULT_REMINDER_SUBJECT = "Нагадування про запис — {{studio}}";
    private static final String DEFAULT_REMINDER_BODY =
            "Привіт, {{client_name}}! Нагадуємо про ваш запис через {{hours_before}}.\n\n" +
            "Не забудьте прийти вчасно. До зустрічі!";

    private static final String DEFAULT_AFTERCARE_SUBJECT = "Догляд після сеансу — {{studio}}";
    private static final String DEFAULT_AFTERCARE_BODY =
            "Привіт, {{client_name}}! Дякуємо, що обрали нас для {{service}}.\n\n" +
            "Поради по догляду:\n" +
            "• Не знімайте захисну плівку протягом 2-4 годин\n" +
            "• Промийте татуювання теплою водою з м'яким милом\n" +
            "• Наносьте тонкий шар загоювального крему 2-3 рази на день\n" +
            "• Уникайте прямих сонячних променів 2-3 тижні\n" +
            "• Не відвідуйте басейн та сауну 2 тижні\n" +
            "• Не чешіть та не здирайте кірочки\n\n" +
            "Якщо виникнуть питання — не вагайтесь звертатися!";

    private static final Map<String, String> FIELD_LABELS = Map.of(
            "service", "Послуга",
            "artist", "Майстер",
            "datetime", "Дата та час",
            "hours_before", "Через"
    );

    public static List<String> getDefaultFields(String type) {
        return switch (type.toUpperCase()) {
            case "CONFIRMATION" -> List.of("service", "artist", "datetime");
            case "REMINDER" -> List.of("service", "artist", "datetime");
            case "AFTERCARE" -> List.of("service");
            default -> List.of();
        };
    }

    public static Map<String, String> getDefaults(String type) {
        return switch (type.toUpperCase()) {
            case "CONFIRMATION" -> Map.of("subject", DEFAULT_CONFIRMATION_SUBJECT, "body", DEFAULT_CONFIRMATION_BODY);
            case "REMINDER" -> Map.of("subject", DEFAULT_REMINDER_SUBJECT, "body", DEFAULT_REMINDER_BODY);
            case "AFTERCARE" -> Map.of("subject", DEFAULT_AFTERCARE_SUBJECT, "body", DEFAULT_AFTERCARE_BODY);
            default -> Map.of("subject", "", "body", "");
        };
    }

    static String formatDateTime(Instant time, String timezone) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", new Locale("uk"));
        return time.atZone(ZoneId.of(timezone)).format(fmt);
    }

    private static String wrap(String title, String bodyContent, String studioName) {
        return """
        <!DOCTYPE html>
        <html lang="uk">
        <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
        <body style="margin:0;padding:0;background:%s;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
          <div style="max-width:560px;margin:0 auto;padding:32px 20px;">
            <div style="text-align:center;margin-bottom:24px;">
              <span style="font-size:24px;font-weight:800;color:%s;letter-spacing:2px;">INKAT</span>
            </div>
            <div style="background:%s;border-radius:12px;padding:32px;border:1px solid #2c2d32;">
              <h2 style="margin:0 0 20px;color:%s;font-size:20px;">%s</h2>
              %s
            </div>
            <div style="text-align:center;margin-top:24px;color:#666;font-size:12px;">
              %s · Powered by INKAT
            </div>
          </div>
        </body>
        </html>
        """.formatted(BG_COLOR, BRAND_COLOR, CARD_BG, TEXT_BRIGHT, title, bodyContent, studioName);
    }

    private static String infoRow(String label, String value) {
        return """
        <div style="display:flex;justify-content:space-between;padding:10px 0;border-bottom:1px solid #2c2d32;">
          <span style="color:%s;font-size:14px;">%s</span>
          <span style="color:%s;font-size:14px;font-weight:600;">%s</span>
        </div>
        """.formatted(TEXT_COLOR, label, TEXT_BRIGHT, value);
    }

    private static String resolveBody(String template, Map<String, String> vars) {
        String resolved = template;
        for (var entry : vars.entrySet()) {
            resolved = resolved.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return resolved;
    }

    private static String textToHtml(String plainText, Map<String, String> vars) {
        String resolved = resolveBody(plainText, vars);
        String[] lines = resolved.split("\n");
        StringBuilder html = new StringBuilder();
        boolean inList = false;
        for (String line : lines) {
            if (line.startsWith("• ")) {
                if (!inList) { html.append("<ul style=\"color:%s;font-size:14px;line-height:1.8;padding-left:20px;margin:12px 0;\">".formatted(TEXT_COLOR)); inList = true; }
                html.append("<li>%s</li>\n".formatted(line.substring(2)));
            } else {
                if (inList) { html.append("</ul>\n"); inList = false; }
                if (line.isBlank()) {
                    html.append("<br/>\n");
                } else {
                    html.append("<p style=\"color:%s;font-size:15px;line-height:1.6;margin:0 0 8px;\">%s</p>\n"
                            .formatted(TEXT_COLOR, line));
                }
            }
        }
        if (inList) html.append("</ul>\n");
        return html.toString();
    }

    private static String buildInfoBlock(List<String> fields, Map<String, String> vars) {
        if (fields == null || fields.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"margin:20px 0;\">\n");
        for (String field : fields) {
            String label = FIELD_LABELS.getOrDefault(field, field);
            String value = vars.getOrDefault(field, "");
            if (!value.isBlank()) {
                sb.append(infoRow(label, value));
            }
        }
        sb.append("</div>\n");
        return sb.toString();
    }

    public static String confirmation(String clientName, String serviceName, String artistName,
                                       Instant startTime, String timezone, String studioName,
                                       String customSubject, String customBody, List<String> customFields) {
        String dateStr = formatDateTime(startTime, timezone);
        Map<String, String> vars = Map.of(
                "client_name", clientName, "service", serviceName,
                "artist", artistName, "datetime", dateStr, "studio", studioName
        );

        String subjectTemplate = customSubject != null ? customSubject : DEFAULT_CONFIRMATION_SUBJECT;
        String bodyTemplate = customBody != null ? customBody : DEFAULT_CONFIRMATION_BODY;
        List<String> fields = customFields != null ? customFields : getDefaultFields("CONFIRMATION");

        String subject = resolveBody(subjectTemplate, vars);
        String bodyHtml = textToHtml(bodyTemplate, vars) + buildInfoBlock(fields, vars);
        return wrap(subject, bodyHtml, studioName);
    }

    public static String reminder(String clientName, String serviceName, String artistName,
                                   Instant startTime, String timezone, String studioName, int hoursBefore,
                                   String customSubject, String customBody, List<String> customFields) {
        String dateStr = formatDateTime(startTime, timezone);
        String timeLabel = hoursBefore >= 24 ? (hoursBefore / 24) + " дн." : hoursBefore + " год.";
        Map<String, String> vars = Map.of(
                "client_name", clientName, "service", serviceName,
                "artist", artistName, "datetime", dateStr, "studio", studioName,
                "hours_before", timeLabel
        );

        String subjectTemplate = customSubject != null ? customSubject : DEFAULT_REMINDER_SUBJECT;
        String bodyTemplate = customBody != null ? customBody : DEFAULT_REMINDER_BODY;
        List<String> fields = customFields != null ? customFields : getDefaultFields("REMINDER");

        String subject = resolveBody(subjectTemplate, vars);
        String bodyHtml = textToHtml(bodyTemplate, vars) + buildInfoBlock(fields, vars);
        return wrap(subject, bodyHtml, studioName);
    }

    public static String aftercare(String clientName, String serviceName, String studioName,
                                    String customSubject, String customBody, List<String> customFields) {
        Map<String, String> vars = Map.of(
                "client_name", clientName, "service", serviceName, "studio", studioName
        );

        String subjectTemplate = customSubject != null ? customSubject : DEFAULT_AFTERCARE_SUBJECT;
        String bodyTemplate = customBody != null ? customBody : DEFAULT_AFTERCARE_BODY;
        List<String> fields = customFields != null ? customFields : getDefaultFields("AFTERCARE");

        String subject = resolveBody(subjectTemplate, vars);
        String bodyHtml = textToHtml(bodyTemplate, vars) + buildInfoBlock(fields, vars);
        return wrap(subject, bodyHtml, studioName);
    }

    public static String cancellation(String clientName, String serviceName,
                                        Instant startTime, String timezone, String studioName,
                                        String customSubject, String customBody) {
        String dateStr = formatDateTime(startTime, timezone);
        Map<String, String> vars = Map.of(
                "client_name", clientName, "service", serviceName,
                "datetime", dateStr, "studio", studioName
        );
        String body = customBody != null ? textToHtml(customBody, vars)
                : textToHtml("Привіт, {{client_name}}! На жаль, ваш запис на {{service}} ({{datetime}}) скасовано.\n\nЗв'яжіться з нами для нового часу.", vars);
        String subj = customSubject != null ? resolveBody(customSubject, vars) : "Запис скасовано — " + studioName;
        body += buildInfoBlock(List.of("service", "datetime"), vars);
        return wrap(subj, body, studioName);
    }

    public static String reschedule(String clientName, String serviceName, String artistName,
                                     Instant newStartTime, String timezone, String studioName,
                                     String customSubject, String customBody) {
        String dateStr = formatDateTime(newStartTime, timezone);
        Map<String, String> vars = Map.of(
                "client_name", clientName, "service", serviceName,
                "artist", artistName, "datetime", dateStr, "studio", studioName
        );
        String body = customBody != null ? textToHtml(customBody, vars)
                : textToHtml("Привіт, {{client_name}}! Час вашого запису на {{service}} змінено.\n\nНовий час: {{datetime}}.", vars);
        String subj = customSubject != null ? resolveBody(customSubject, vars) : "Час запису змінено — " + studioName;
        body += buildInfoBlock(List.of("service", "artist", "datetime"), vars);
        return wrap(subj, body, studioName);
    }

    public static String staffNewAppointment(String artistName, String clientName, String serviceName,
                                              Instant startTime, String timezone, String studioName) {
        String dateStr = formatDateTime(startTime, timezone);
        Map<String, String> vars = Map.of(
                "artist_name", artistName, "client_name", clientName,
                "service", serviceName, "datetime", dateStr, "studio", studioName
        );
        String body = textToHtml("Привіт, {{artist_name}}! До вас новий запис.\n\nКлієнт: {{client_name}}\nПослуга: {{service}}\nЧас: {{datetime}}", vars);
        return wrap("Новий запис — " + clientName, body, studioName);
    }

    public static String staffCancellation(String artistName, String clientName, String serviceName,
                                            Instant startTime, String timezone, String studioName) {
        String dateStr = formatDateTime(startTime, timezone);
        Map<String, String> vars = Map.of(
                "artist_name", artistName, "client_name", clientName,
                "service", serviceName, "datetime", dateStr, "studio", studioName
        );
        String body = textToHtml("Привіт, {{artist_name}}! Клієнт {{client_name}} скасував запис.\n\nПослуга: {{service}}\nБув час: {{datetime}}", vars);
        return wrap("Скасування запису — " + clientName, body, studioName);
    }

    public static String staffReschedule(String artistName, String clientName, String serviceName,
                                          Instant newStartTime, String timezone, String studioName) {
        String dateStr = formatDateTime(newStartTime, timezone);
        Map<String, String> vars = Map.of(
                "artist_name", artistName, "client_name", clientName,
                "service", serviceName, "datetime", dateStr, "studio", studioName
        );
        String body = textToHtml("Привіт, {{artist_name}}! Час запису клієнта {{client_name}} змінено.\n\nПослуга: {{service}}\nНовий час: {{datetime}}", vars);
        return wrap("Перенесення запису — " + clientName, body, studioName);
    }

    public static String manual(String subject, String textBody, String studioName) {
        String body = """
            <div style="color:%s;font-size:15px;line-height:1.7;white-space:pre-wrap;">%s</div>
            """.formatted(TEXT_COLOR, textBody);
        return wrap(subject, body, studioName);
    }
}
