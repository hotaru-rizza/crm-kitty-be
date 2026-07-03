package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.dto.EmailLayoutContext;
import com.inkflow.crm.module.email.enums.TemplateCategory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailLayoutTest {

    @Test
    void wrap_usesInkDarkLayoutAndBrandAssets() {
        EmailLayoutContext context = new EmailLayoutContext(
                "INKAT",
                "Ваш запис підтверджено",
                EmailLayout.toHtml("Привіт, Олено!"),
                TemplateCategory.CLIENT_OP,
                "InkFlow Studio",
                "https://app.inkat.studio/action",
                "Переглянути запис"
        );

        String html = EmailLayout.wrap(context);

        assertThat(html).contains("#060608");
        assertThat(html).contains("data:image/png;base64,");
        assertThat(html).contains("INKAT");
        assertThat(html).contains("Ваш запис підтверджено");
        assertThat(html).contains("Привіт, Олено!");
        assertThat(html).contains("https://app.inkat.studio/action");
        assertThat(html).contains("Переглянути запис →");
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
                null
        );

        String html = EmailLayout.wrap(context);

        assertThat(html).contains("Відписатися");
    }
}
