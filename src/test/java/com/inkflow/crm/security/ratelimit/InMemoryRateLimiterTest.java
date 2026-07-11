package com.inkflow.crm.security.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class InMemoryRateLimiterTest {

    private InMemoryRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(true);

        RateLimitRule rule = new RateLimitRule();
        rule.setPattern("/staff/invite/info/**");
        rule.setMethods("GET");
        rule.setMaxRequests(2);
        rule.setWindow(Duration.ofMinutes(1));
        properties.setRules(java.util.List.of(rule));

        rateLimiter = new InMemoryRateLimiter(properties);
    }

    @Test
    void tryConsume_allowsUntilLimitThenBlocks() {
        String ip = "203.0.113.10";

        assertTrue(rateLimiter.tryConsume(ip, "GET", "/staff/invite/info/abc"));
        assertTrue(rateLimiter.tryConsume(ip, "GET", "/staff/invite/info/abc"));
        assertFalse(rateLimiter.tryConsume(ip, "GET", "/staff/invite/info/abc"));
    }

    @Test
    void tryConsume_ignoresUnmatchedPaths() {
        assertTrue(rateLimiter.tryConsume("203.0.113.11", "POST", "/onboarding"));
    }
}
