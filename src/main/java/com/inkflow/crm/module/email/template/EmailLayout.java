package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.dto.EmailLayoutContext;
import com.inkflow.crm.module.email.enums.TemplateCategory;

public final class EmailLayout {

    private EmailLayout() {}

    public static String wrap(EmailLayoutContext context) {
        return """
        <!DOCTYPE html>
        <html lang="uk">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <meta http-equiv="X-UA-Compatible" content="IE=edge">
        </head>
        <body style="margin:0;padding:0;background-color:%s;-webkit-text-size-adjust:100%%;-ms-text-size-adjust:100%%;">
          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:%s;">
            <tr>
              <td align="center" style="padding:40px 20px 48px;background-color:%s;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:520px;">
                  %s
                  <tr>
                    <td style="padding:0 0 28px;font-family:%s;font-size:22px;font-weight:600;line-height:1.3;letter-spacing:-0.02em;color:%s;">
                      %s
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:0;font-family:%s;font-size:15px;line-height:1.65;color:%s;">
                      %s
                    </td>
                  </tr>
                  %s
                  <tr>
                    <td style="padding:32px 0 16px;">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                        <tr>
                          <td height="1" style="height:1px;background-color:%s;font-size:0;line-height:0;">&nbsp;</td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                  %s
                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
        """.formatted(
                EmailBrandAssets.BG_COLOR,
                EmailBrandAssets.BG_COLOR,
                EmailBrandAssets.BG_COLOR,
                buildBrandRow(context.studioName(), context.studioLogoUrl()),
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.TEXT_PRIMARY,
                escapeHtml(context.title()),
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.TEXT_BODY,
                context.bodyHtml(),
                buildButton(context.actionUrl(), context.actionLabel()),
                EmailBrandAssets.BORDER_SUBTLE,
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
                if (inOrderedList) {
                    html.append("</ol>\n");
                    inOrderedList = false;
                }
                if (!inBulletList) {
                    html.append(ulTag());
                    inBulletList = true;
                }
                html.append("<li style=\"margin:0 0 6px;\">%s</li>\n".formatted(escapeHtml(line.substring(2))));
                continue;
            }

            if (line.matches("^\\d+\\. .+")) {
                if (inBulletList) {
                    html.append("</ul>\n");
                    inBulletList = false;
                }
                if (!inOrderedList) {
                    html.append(olTag());
                    inOrderedList = true;
                }
                html.append("<li style=\"margin:0 0 6px;\">%s</li>\n"
                        .formatted(escapeHtml(line.replaceFirst("^\\d+\\. ", ""))));
                continue;
            }

            if (inBulletList) {
                html.append("</ul>\n");
                inBulletList = false;
            }
            if (inOrderedList) {
                html.append("</ol>\n");
                inOrderedList = false;
            }

            if (line.isBlank()) {
                html.append("<div style=\"height:12px;line-height:12px;font-size:0;\">&nbsp;</div>\n");
            } else {
                html.append("<p style=\"color:%s;font-size:15px;line-height:1.65;margin:0 0 12px;\">%s</p>\n"
                        .formatted(EmailBrandAssets.TEXT_BODY, escapeHtml(line)));
            }
        }

        if (inBulletList) {
            html.append("</ul>\n");
        }
        if (inOrderedList) {
            html.append("</ol>\n");
        }

        return html.toString();
    }

    private static String buildBrandRow(String studioName, String studioLogoUrl) {
        boolean hasStudioLogo = studioLogoUrl != null && !studioLogoUrl.isBlank();

        String logoCell;
        if (hasStudioLogo) {
            logoCell = """
                  <td style="padding-right:10px;vertical-align:middle;">
                    <img src="%s" alt="%s" width="28" height="28" style="display:block;width:28px;height:28px;border:0;border-radius:8px;object-fit:cover;" />
                  </td>
                """.formatted(escapeHtml(studioLogoUrl), escapeHtml(studioName));
        } else {
            String platformLogo = EmailBrandAssets.logoDataUri();
            logoCell = platformLogo.isBlank()
                    ? ""
                    : """
                  <td style="padding-right:10px;vertical-align:middle;">
                    <img src="%s" alt="%s" width="28" height="28" style="display:block;width:28px;height:28px;border:0;border-radius:50%%;object-fit:contain;" />
                  </td>
                """.formatted(platformLogo, escapeHtml(studioName));
        }

        return """
                  <tr>
                    <td style="padding-bottom:32px;">
                      <table role="presentation" cellspacing="0" cellpadding="0" border="0">
                        <tr>
                          %s
                          <td style="vertical-align:middle;font-family:%s;font-size:13px;font-weight:600;letter-spacing:0.04em;text-transform:uppercase;color:%s;">
                            %s
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                """.formatted(
                logoCell,
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.TEXT_MUTED,
                escapeHtml(studioName)
        );
    }

    private static String ulTag() {
        return "<ul style=\"color:%s;font-size:15px;line-height:1.65;padding-left:18px;margin:4px 0 16px;\">\n"
                .formatted(EmailBrandAssets.TEXT_BODY);
    }

    private static String olTag() {
        return "<ol style=\"color:%s;font-size:15px;line-height:1.65;padding-left:18px;margin:4px 0 16px;\">\n"
                .formatted(EmailBrandAssets.TEXT_BODY);
    }

    private static String buildButton(String url, String label) {
        if (url == null || url.isBlank()) {
            return "";
        }

        String buttonLabel = (label != null && !label.isBlank()) ? escapeHtml(label) : "Перейти";

        return """
                  <tr>
                    <td style="padding:28px 0 0;">
                      <table role="presentation" cellspacing="0" cellpadding="0" border="0">
                        <tr>
                          <td style="border-radius:8px;background-color:%s;">
                            <a href="%s" style="display:inline-block;padding:12px 22px;font-family:%s;font-size:14px;font-weight:600;line-height:1;color:%s;text-decoration:none;border-radius:8px;">
                              %s
                            </a>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                """.formatted(
                EmailBrandAssets.BUTTON_BG,
                url,
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.BUTTON_TEXT,
                buttonLabel
        );
    }

    private static String buildFooter(String appName, String studioName, TemplateCategory category) {
        String studioLine = """
                  <tr>
                    <td style="padding:0;font-family:%s;font-size:12px;line-height:1.5;color:%s;">
                      %s
                    </td>
                  </tr>
                """.formatted(
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.TEXT_FOOTER,
                escapeHtml(studioName)
        );

        String poweredBy = """
                  <tr>
                    <td style="padding:4px 0 0;font-family:%s;font-size:11px;line-height:1.5;color:%s;">
                      Powered by %s
                    </td>
                  </tr>
                """.formatted(
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.TEXT_MUTED,
                escapeHtml(appName)
        );

        String base = studioLine + poweredBy;

        if (category != TemplateCategory.MARKETING) {
            return base;
        }

        return base + """
                  <tr>
                    <td style="padding:16px 0 0;font-family:%s;font-size:11px;line-height:1.5;color:%s;">
                      Ви отримали цей лист, оскільки погодилися на маркетингові розсилки.<br/>
                      <a href="{action_url}" style="color:%s;text-decoration:underline;">Відписатися</a>
                    </td>
                  </tr>
                """.formatted(
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.TEXT_MUTED,
                EmailBrandAssets.TEXT_MUTED
        );
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
