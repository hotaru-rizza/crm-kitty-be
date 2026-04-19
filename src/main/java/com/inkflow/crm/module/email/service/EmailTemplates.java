package com.inkflow.crm.module.email.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class EmailTemplates {

    private EmailTemplates() {}

    private static final String BRAND_COLOR = "#c026d3";
    private static final String BG_COLOR = "#1a1b1e";
    private static final String CARD_BG = "#25262b";
    private static final String TEXT_COLOR = "#c1c2c5";
    private static final String TEXT_BRIGHT = "#ffffff";

    private static String formatDateTime(Instant time, String timezone) {
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

    public static String confirmation(String clientName, String serviceName, String artistName,
                                       Instant startTime, String timezone, String studioName) {
        String dateStr = formatDateTime(startTime, timezone);
        String body = """
            <p style="color:%s;font-size:15px;line-height:1.6;margin:0 0 20px;">
              Привіт, <strong style="color:%s">%s</strong>! Ваш запис підтверджено.
            </p>
            <div style="margin:20px 0;">
              %s
              %s
              %s
            </div>
            <p style="color:%s;font-size:13px;margin:20px 0 0;line-height:1.5;">
              Якщо потрібно скасувати або перенести — зв'яжіться з нами завчасно.
            </p>
            """.formatted(
                TEXT_COLOR, TEXT_BRIGHT, clientName,
                infoRow("Послуга", serviceName),
                infoRow("Майстер", artistName),
                infoRow("Дата та час", dateStr),
                TEXT_COLOR
        );
        return wrap("Запис підтверджено ✓", body, studioName);
    }

    public static String reminder(String clientName, String serviceName, String artistName,
                                   Instant startTime, String timezone, String studioName, int hoursBefore) {
        String dateStr = formatDateTime(startTime, timezone);
        String timeLabel = hoursBefore >= 24 ? (hoursBefore / 24) + " дн." : hoursBefore + " год.";
        String body = """
            <p style="color:%s;font-size:15px;line-height:1.6;margin:0 0 20px;">
              Привіт, <strong style="color:%s">%s</strong>! Нагадуємо про ваш запис через <strong style="color:%s">%s</strong>
            </p>
            <div style="margin:20px 0;">
              %s
              %s
              %s
            </div>
            <p style="color:%s;font-size:13px;margin:20px 0 0;line-height:1.5;">
              Не забудьте прийти вчасно. До зустрічі!
            </p>
            """.formatted(
                TEXT_COLOR, TEXT_BRIGHT, clientName, BRAND_COLOR, timeLabel,
                infoRow("Послуга", serviceName),
                infoRow("Майстер", artistName),
                infoRow("Дата та час", dateStr),
                TEXT_COLOR
        );
        return wrap("Нагадування про запис", body, studioName);
    }

    public static String aftercare(String clientName, String serviceName, String studioName) {
        String body = """
            <p style="color:%s;font-size:15px;line-height:1.6;margin:0 0 20px;">
              Привіт, <strong style="color:%s">%s</strong>! Дякуємо, що обрали нас для <strong>%s</strong>.
            </p>
            <div style="background:#1a1b1e;border-radius:8px;padding:20px;margin:20px 0;">
              <h3 style="color:%s;margin:0 0 12px;font-size:16px;">Поради по догляду:</h3>
              <ul style="color:%s;font-size:14px;line-height:1.8;padding-left:20px;margin:0;">
                <li>Не знімайте захисну плівку протягом 2-4 годин</li>
                <li>Промийте татуювання теплою водою з м'яким милом</li>
                <li>Наносьте тонкий шар загоювального крему 2-3 рази на день</li>
                <li>Уникайте прямих сонячних променів 2-3 тижні</li>
                <li>Не відвідуйте басейн та сауну 2 тижні</li>
                <li>Не чешіть та не здирайте кірочки</li>
              </ul>
            </div>
            <p style="color:%s;font-size:13px;margin:20px 0 0;line-height:1.5;">
              Якщо виникнуть питання — не вагайтесь звертатися!
            </p>
            """.formatted(
                TEXT_COLOR, TEXT_BRIGHT, clientName, serviceName,
                TEXT_BRIGHT, TEXT_COLOR, TEXT_COLOR
        );
        return wrap("Догляд після сеансу", body, studioName);
    }

    public static String manual(String subject, String textBody, String studioName) {
        String body = """
            <div style="color:%s;font-size:15px;line-height:1.7;white-space:pre-wrap;">%s</div>
            """.formatted(TEXT_COLOR, textBody);
        return wrap(subject, body, studioName);
    }
}
