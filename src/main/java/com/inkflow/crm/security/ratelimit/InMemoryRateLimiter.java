package com.inkflow.crm.security.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class InMemoryRateLimiter {

    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Map<String, BucketState> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String clientIp, String method, String path) {
        if (!properties.isEnabled()) {
            return true;
        }

        RateLimitRule rule = resolveRule(method, path);
        if (rule == null) {
            return true;
        }

        String bucketKey = rule.getPattern() + "|" + rule.getMethods() + "|" + clientIp;
        long windowMillis = rule.getWindow().toMillis();
        int maxRequests = rule.getMaxRequests();
        long now = System.currentTimeMillis();

        synchronized (buckets.computeIfAbsent(bucketKey, ignored -> new BucketState())) {
            BucketState state = buckets.get(bucketKey);
            state.pruneOlderThan(now - windowMillis);

            if (state.timestamps.size() >= maxRequests) {
                return false;
            }

            state.timestamps.add(now);
            return true;
        }
    }

    public Duration retryAfter(String clientIp, String method, String path) {
        RateLimitRule rule = resolveRule(method, path);
        if (rule == null) {
            return Duration.ZERO;
        }

        String bucketKey = rule.getPattern() + "|" + rule.getMethods() + "|" + clientIp;
        BucketState state = buckets.get(bucketKey);
        if (state == null || state.timestamps.isEmpty()) {
            return rule.getWindow();
        }

        long oldest = state.timestamps.getFirst();
        long retryAt = oldest + rule.getWindow().toMillis();
        long remaining = retryAt - System.currentTimeMillis();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    private RateLimitRule resolveRule(String method, String path) {
        for (RateLimitRule rule : properties.getRules()) {
            if (!matchesMethod(rule.getMethods(), method)) {
                continue;
            }
            if (pathMatcher.match(rule.getPattern(), path)) {
                return rule;
            }
        }
        return null;
    }

    private boolean matchesMethod(String configuredMethods, String requestMethod) {
        if (!org.springframework.util.StringUtils.hasText(configuredMethods) || "*".equals(configuredMethods)) {
            return true;
        }

        for (String allowed : configuredMethods.split(",")) {
            if (allowed.trim().equalsIgnoreCase(requestMethod)) {
                return true;
            }
        }
        return false;
    }

    private static final class BucketState {
        private final List<Long> timestamps = new ArrayList<>();

        private void pruneOlderThan(long cutoff) {
            timestamps.removeIf(timestamp -> timestamp < cutoff);
        }
    }
}
