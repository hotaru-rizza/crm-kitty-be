package com.inkflow.crm.module.consumer.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "inkflow.consumer.ai")
public class ConsumerAiProperties {

    private int welcomeCredits = 5;
    private Cost cost = new Cost();

    @Getter
    @Setter
    public static class Cost {
        private int generation = 1;
        private int tryOn = 1;
    }
}
