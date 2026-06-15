package com.inkflow.crm.module.email.template;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

public final class EmailHtmlSanitizer {

    private static final Safelist EMAIL_SAFELIST = new Safelist()
            .addTags("p", "br", "b", "strong", "i", "em", "u", "ul", "ol", "li", "a", "span", "h1", "h2", "h3")
            .addAttributes("a", "href", "rel", "target")
            .addAttributes("span", "style", "data-var")
            .addAttributes("p", "style")
            .addAttributes("h1", "style")
            .addAttributes("h2", "style")
            .addAttributes("h3", "style")
            .addProtocols("a", "href", "http", "https", "mailto");

    private EmailHtmlSanitizer() {}

    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        String cleaned = Jsoup.clean(html, EMAIL_SAFELIST);
        Document doc = Jsoup.parseBodyFragment(cleaned);
        doc.select("a[href]").forEach(EmailHtmlSanitizer::hardenLink);
        return doc.body().html();
    }

    private static void hardenLink(Element link) {
        link.attr("rel", "nofollow noopener noreferrer");
        link.attr("target", "_blank");
    }
}
