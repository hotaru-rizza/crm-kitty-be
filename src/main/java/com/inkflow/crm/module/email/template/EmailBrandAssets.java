package com.inkflow.crm.module.email.template;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class EmailBrandAssets {

    static final String BG_COLOR = "#060608";
    static final String TEXT_PRIMARY = "#ffffff";
    static final String TEXT_BODY = "rgba(255,255,255,0.82)";
    static final String TEXT_MUTED = "rgba(255,255,255,0.35)";
    static final String TEXT_FOOTER = "rgba(255,255,255,0.2)";
    static final String BORDER_SUBTLE = "rgba(255,255,255,0.06)";
    static final String CARD_BG = "rgba(255,255,255,0.04)";
    static final String CARD_BORDER = "rgba(255,255,255,0.07)";
    static final String FONT_STACK =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
    static final String TOP_GLOW =
            "radial-gradient(ellipse 100% 140px at 50% 0%, rgba(180,140,255,0.18) 0%, rgba(140,100,240,0.06) 50%, transparent 85%)";
    static final String BOTTOM_GLOW =
            "radial-gradient(ellipse 80% 80px at 50% 100%, rgba(180,140,255,0.12) 0%, rgba(140,100,240,0.04) 45%, transparent 80%)";

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
