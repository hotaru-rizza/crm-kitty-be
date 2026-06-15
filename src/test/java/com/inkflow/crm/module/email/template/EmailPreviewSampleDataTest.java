package com.inkflow.crm.module.email.template;

import com.inkflow.crm.module.email.enums.TemplateVar;
import com.inkflow.crm.module.email.enums.TriggerType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailPreviewSampleDataTest {

    @Test
    void forTrigger_usesReadableSampleValues() {
        var variables = EmailPreviewSampleData.forTrigger(
                TriggerType.BOOKING_CONFIRMED,
                "INKAT",
                "Ink Studio"
        );

        assertThat(variables.get(TemplateVar.CLIENT_NAME.getPlaceholder())).isEqualTo("Олена");
        assertThat(variables.get(TemplateVar.MASTER_NAME.getPlaceholder())).isEqualTo("Катерина");
        assertThat(variables.get(TemplateVar.APP_NAME.getPlaceholder())).isEqualTo("INKAT");
        assertThat(variables.get(TemplateVar.STUDIO_NAME.getPlaceholder())).isEqualTo("Ink Studio");
        assertThat(variables.get(TemplateVar.CLIENT_NAME.getPlaceholder())).doesNotContain("[");
    }
}
