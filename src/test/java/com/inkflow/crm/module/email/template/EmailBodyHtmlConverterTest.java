package com.inkflow.crm.module.email.template;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailBodyHtmlConverterTest {

    @Test
    void toHtml_convertsPlainTextLinesToParagraphs() {
        String result = EmailBodyHtmlConverter.toHtml("Hello\n\nWorld");

        assertThat(result).contains("<p");
        assertThat(result).contains("Hello");
        assertThat(result).contains("World");
    }

    @Test
    void toHtml_sanitizesStoredHtmlBody() {
        String input = "<p>Hello <strong>world</strong></p><script>alert(1)</script>";

        String result = EmailBodyHtmlConverter.toHtml(input);

        assertThat(result).contains("<strong>world</strong>");
        assertThat(result).doesNotContain("script");
    }
}
