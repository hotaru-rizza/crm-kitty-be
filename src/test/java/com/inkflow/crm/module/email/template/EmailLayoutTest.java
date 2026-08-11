package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.dto.EmailLayoutContext;
import com.inkflow.crm.module.email.enums.TemplateCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailLayoutTest {

    @Test
    void wrap_usesMinimalDarkLayoutAndBrandAssets() {
        EmailLayoutContext context = new EmailLayoutContext(
                "Inkat CRM",
                "Ваш запис підтверджено",
                EmailLayout.toHtml("Привіт, Олено!"),
                TemplateCategory.CLIENT_OP,
                "InkFlow Studio",
                "https://studio.example.com/logo.png",
                "https://app.inkat.studio/action",
                "Переглянути запис"
        );

        String html = EmailLayout.wrap(context);

        assertThat(html).contains("#0a0a0b");
        assertThat(html).contains("https://studio.example.com/logo.png");
        assertThat(html).contains("InkFlow Studio");
        assertThat(html).contains("Powered by Inkat CRM");
        assertThat(html).contains("Ваш запис підтверджено");
        assertThat(html).contains("Привіт, Олено!");
        assertThat(html).contains("https://app.inkat.studio/action");
        assertThat(html).contains("Переглянути запис");
        assertThat(html).doesNotContain("radial-gradient");
        assertThat(html).doesNotContain("border-radius:16px");
        assertThat(html).doesNotContain("#c026d3");
        assertThat(html).doesNotContain("#25262b");
    }

    @Test
    void wrap_includesMarketingUnsubscribeFooter() {
        EmailLayoutContext context = new EmailLayoutContext(
                "INKAT",
                "Акція",
                "<p>Body</p>",
                TemplateCategory.MARKETING,
                "Studio",
                null,
                null,
                null
        );

        String html = EmailLayout.wrap(context);

        assertThat(html).contains("Відписатися");
    }
}
