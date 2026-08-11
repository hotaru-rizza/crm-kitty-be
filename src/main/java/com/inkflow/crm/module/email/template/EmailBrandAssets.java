package com.inkflow.crm.module.email.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class EmailBrandAssets {

    static final String BG_COLOR = "#0a0a0b";
    static final String TEXT_PRIMARY = "#f5f5f5";
    static final String TEXT_BODY = "rgba(245,245,245,0.78)";
    static final String TEXT_MUTED = "rgba(245,245,245,0.38)";
    static final String TEXT_FOOTER = "rgba(245,245,245,0.28)";
    static final String BORDER_SUBTLE = "rgba(255,255,255,0.08)";
    static final String BUTTON_BG = "#f5f5f5";
    static final String BUTTON_TEXT = "#0a0a0b";
    static final String FONT_STACK =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private static final String LOGO_DATA_URI = loadLogoDataUri();

    private EmailBrandAssets() {}

    static String logoDataUri() {
        return LOGO_DATA_URI;
    }

    private static String loadLogoDataUri() {
        try (InputStream stream = EmailBrandAssets.class.getResourceAsStream("/email/assets/inkat-logo-52.base64")) {
            if (stream == null) {
                return "";
            }

            String base64 = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            return base64.isBlank() ? "" : "data:image/png;base64," + base64;
        } catch (IOException e) {
            return "";
        }
    }
}
