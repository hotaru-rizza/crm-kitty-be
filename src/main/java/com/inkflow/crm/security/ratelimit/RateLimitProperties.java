package com.inkflow.crm.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "inkflow.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private List<RateLimitRule> rules = defaultRules();

    private static List<RateLimitRule> defaultRules() {
        List<RateLimitRule> rules = new ArrayList<>();

        RateLimitRule inviteInfo = new RateLimitRule();
        inviteInfo.setPattern("/staff/invite/info/**");
        inviteInfo.setMethods("GET");
        inviteInfo.setMaxRequests(30);
        inviteInfo.setWindow(Duration.ofMinutes(1));
        rules.add(inviteInfo);

        RateLimitRule acceptInvite = new RateLimitRule();
        acceptInvite.setPattern("/staff/accept-invite");
        acceptInvite.setMethods("POST");
        acceptInvite.setMaxRequests(10);
        acceptInvite.setWindow(Duration.ofMinutes(1));
        rules.add(acceptInvite);

        RateLimitRule onboarding = new RateLimitRule();
        onboarding.setPattern("/onboarding");
        onboarding.setMethods("POST");
        onboarding.setMaxRequests(5);
        onboarding.setWindow(Duration.ofHours(1));
        rules.add(onboarding);

        RateLimitRule monobankWebhook = new RateLimitRule();
        monobankWebhook.setPattern("/payments/monobank/webhook");
        monobankWebhook.setMethods("POST");
        monobankWebhook.setMaxRequests(120);
        monobankWebhook.setWindow(Duration.ofMinutes(1));
        rules.add(monobankWebhook);

        return rules;
    }
}
