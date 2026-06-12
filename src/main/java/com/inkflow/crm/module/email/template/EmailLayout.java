package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.dto.EmailLayoutContext;
import com.inkflow.crm.module.email.enums.TemplateCategory;

public final class EmailLayout {

    private static final String BRAND_COLOR = "#c026d3";
    private static final String BG_COLOR = "#1a1b1e";
    private static final String CARD_BG = "#25262b";
    private static final String TEXT_COLOR = "#c1c2c5";
    private static final String TEXT_BRIGHT = "#ffffff";

    private EmailLayout() {}

    public static String wrap(EmailLayoutContext context) {
        return """
        <!DOCTYPE html>
        <html lang="uk">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
        </head>
        <body style="margin:0;padding:0;background:%s;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;">
          <div style="max-width:560px;margin:0 auto;padding:32px 20px;">
            <div style="text-align:center;margin-bottom:24px;">
              <span style="font-size:24px;font-weight:800;color:%s;letter-spacing:2px;">%s</span>
            </div>
            <div style="background:%s;border-radius:12px;padding:32px;border:1px solid #2c2d32;">
              <h2 style="margin:0 0 20px;color:%s;font-size:20px;">%s</h2>
              %s
              %s
            </div>
            %s
          </div>
        </body>
        </html>
        """.formatted(
                BG_COLOR,
                BRAND_COLOR,
                escapeHtml(context.appName()),
                CARD_BG,
                TEXT_BRIGHT,
                escapeHtml(context.title()),
                context.bodyHtml(),
                buildButton(context.actionUrl(), context.actionLabel()),
                buildFooter(context.appName(), context.studioName(), context.category())
        );
    }

    public static String toHtml(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            return "";
        }

        String[] lines = plainText.split("\n");
        StringBuilder html = new StringBuilder();
        boolean inBulletList = false;
        boolean inOrderedList = false;

        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();

            if (line.startsWith("• ") || line.startsWith("- ")) {
                inOrderedList = closeOrderedList(html, inOrderedList);
                inBulletList = openBulletList(html, inBulletList);
                html.append("<li>%s</li>\n".formatted(escapeHtml(line.substring(2))));
                continue;
            }

            if (line.matches("^\\d+\\. .+")) {
                inBulletList = closeBulletList(html, inBulletList);
                inOrderedList = openOrderedList(html, inOrderedList);
                html.append("<li>%s</li>\n".formatted(escapeHtml(line.replaceFirst("^\\d+\\. ", ""))));
                continue;
            }

            inBulletList = closeBulletList(html, inBulletList);
            inOrderedList = closeOrderedList(html, inOrderedList);

            if (line.isBlank()) {
                html.append("<br/>\n");
            } else {
                html.append("<p style=\"color:%s;font-size:15px;line-height:1.6;margin:0 0 8px;\">%s</p>\n"
                        .formatted(TEXT_COLOR, escapeHtml(line)));
            }
        }

        closeBulletList(html, inBulletList);
        closeOrderedList(html, inOrderedList);

        return html.toString();
    }

    private static boolean openBulletList(StringBuilder html, boolean inBulletList) {
        if (!inBulletList) {
            html.append("<ul style=\"color:%s;font-size:14px;line-height:1.8;padding-left:20px;margin:12px 0;\">\n"
                    .formatted(TEXT_COLOR));
            return true;
        }
        return inBulletList;
    }

    private static boolean openOrderedList(StringBuilder html, boolean inOrderedList) {
        if (!inOrderedList) {
            html.append("<ol style=\"color:%s;font-size:14px;line-height:1.8;padding-left:20px;margin:12px 0;\">\n"
                    .formatted(TEXT_COLOR));
            return true;
        }
        return inOrderedList;
    }

    private static boolean closeBulletList(StringBuilder html, boolean inBulletList) {
        if (inBulletList) {
            html.append("</ul>\n");
            return false;
        }
        return inBulletList;
    }

    private static boolean closeOrderedList(StringBuilder html, boolean inOrderedList) {
        if (inOrderedList) {
            html.append("</ol>\n");
            return false;
        }
        return inOrderedList;
    }

    private static String buildButton(String url, String label) {
        if (url == null || url.isBlank()) {
            return "";
        }

        String buttonLabel = (label != null && !label.isBlank()) ? escapeHtml(label) : "Перейти";

        return """
        <div style="text-align:center;margin:24px 0 0;">
          <a href="%s" style="background:%s;color:#fff;text-decoration:none;padding:12px 28px;border-radius:8px;font-size:15px;font-weight:600;display:inline-block;">%s</a>
        </div>
        """.formatted(url, BRAND_COLOR, buttonLabel);
    }

    private static String buildFooter(String appName, String studioName, TemplateCategory category) {
        String base = """
        <div style="text-align:center;margin-top:24px;color:#666;font-size:12px;">
          %s · Powered by %s
        </div>
        """.formatted(escapeHtml(studioName), escapeHtml(appName));

        if (category != TemplateCategory.MARKETING) {
            return base;
        }

        return base + """
        <div style="text-align:center;margin-top:8px;color:#555;font-size:11px;">
          Ви отримали цей лист, оскільки погодилися на маркетингові розсилки.<br/>
          <a href="{action_url}" style="color:#888;">Відписатися</a>
        </div>
        """;
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
