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
          <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:%s;min-height:100%%;">
            <tr>
              <td align="center" style="padding:28px 20px 48px;background-color:%s;background-image:%s;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:480px;">
                  %s
                  <tr>
                    <td align="center" style="padding:0 8px 20px;font-family:%s;font-size:26px;font-weight:700;line-height:1.25;letter-spacing:-0.025em;color:%s;">
                      %s
                    </td>
                  </tr>
                  <tr>
                    <td style="padding:0 8px 8px;font-family:%s;font-size:15px;line-height:1.6;color:%s;">
                      %s
                    </td>
                  </tr>
                  %s
                  <tr>
                    <td style="padding:24px 24px 24px;">
                      <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                        <tr>
                          <td height="1" style="height:1px;background-color:%s;font-size:0;line-height:0;">&nbsp;</td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                  %s
                </table>
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:480px;margin-top:32px;">
                  <tr>
                    <td height="80" style="height:80px;background:%s;font-size:0;line-height:0;">&nbsp;</td>
                  </tr>
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
                EmailBrandAssets.TOP_GLOW,
                buildBrandRow(context.appName()),
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.TEXT_PRIMARY,
                escapeHtml(context.title()),
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.TEXT_BODY,
                context.bodyHtml(),
                buildButton(context.actionUrl(), context.actionLabel()),
                EmailBrandAssets.BORDER_SUBTLE,
                buildFooter(context.appName(), context.studioName(), context.category()),
                EmailBrandAssets.BOTTOM_GLOW
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
                html.append("<li>%s</li>\n".formatted(escapeHtml(line.substring(2))));
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
                html.append("<li>%s</li>\n".formatted(escapeHtml(line.replaceFirst("^\\d+\\. ", ""))));
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
                html.append("<br/>\n");
            } else {
                html.append("<p style=\"color:%s;font-size:15px;line-height:1.6;margin:0 0 10px;\">%s</p>\n"
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

    private static String buildBrandRow(String appName) {
        String logo = EmailBrandAssets.logoDataUri();
        String logoCell = logo.isBlank()
                ? ""
                : """
                  <td style="padding-right:10px;vertical-align:middle;">
                    <img src="%s" alt="%s" width="52" height="52" style="display:block;width:52px;height:52px;border:0;border-radius:50%%;object-fit:contain;" />
                  </td>
                """.formatted(logo, escapeHtml(appName));

        return """
                  <tr>
                    <td align="center" style="padding-bottom:28px;">
                      <table role="presentation" cellspacing="0" cellpadding="0" border="0" align="center">
                        <tr>
                          %s
                          <td style="vertical-align:middle;font-family:%s;font-size:20px;font-weight:700;letter-spacing:-0.03em;color:%s;">
                            %s
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                """.formatted(
                logoCell,
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.TEXT_PRIMARY,
                escapeHtml(appName)
        );
    }

    private static String ulTag() {
        return "<ul style=\"color:%s;font-size:14px;line-height:1.8;padding-left:20px;margin:12px 0;\">\n"
                .formatted(EmailBrandAssets.TEXT_BODY);
    }

    private static String olTag() {
        return "<ol style=\"color:%s;font-size:14px;line-height:1.8;padding-left:20px;margin:12px 0;\">\n"
                .formatted(EmailBrandAssets.TEXT_BODY);
    }

    private static String buildButton(String url, String label) {
        if (url == null || url.isBlank()) {
            return "";
        }

        String buttonLabel = (label != null && !label.isBlank()) ? escapeHtml(label) : "Перейти";

        return """
                  <tr>
                    <td align="center" style="padding:20px 0 8px;">
                      <table role="presentation" cellspacing="0" cellpadding="0" border="0">
                        <tr>
                          <td align="center" style="border-radius:12px;background-color:#ffffff;">
                            <a href="%s" style="display:inline-block;padding:14px 36px;font-family:%s;font-size:15px;font-weight:600;line-height:1;color:%s;text-decoration:none;border-radius:12px;border:1px solid rgba(255,255,255,0.8);">
                              %s →
                            </a>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                """.formatted(url, EmailBrandAssets.FONT_STACK, EmailBrandAssets.BG_COLOR, buttonLabel);
    }

    private static String buildFooter(String appName, String studioName, TemplateCategory category) {
        String base = """
                  <tr>
                    <td align="center" style="padding:0 12px;font-family:%s;font-size:12px;line-height:1.6;color:%s;">
                      %s · %s
                    </td>
                  </tr>
                """.formatted(
                EmailBrandAssets.FONT_STACK,
                EmailBrandAssets.TEXT_FOOTER,
                escapeHtml(studioName),
                escapeHtml(appName)
        );

        if (category != TemplateCategory.MARKETING) {
            return base;
        }

        return base + """
                  <tr>
                    <td align="center" style="padding:12px 12px 0;font-family:%s;font-size:11px;line-height:1.6;color:%s;">
                      Ви отримали цей лист, оскільки погодилися на маркетингові розсилки.<br/>
                      <a href="{action_url}" style="color:rgba(255,255,255,0.35);text-decoration:underline;">Відписатися</a>
                    </td>
                  </tr>
                """.formatted(EmailBrandAssets.FONT_STACK, EmailBrandAssets.TEXT_MUTED);
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
