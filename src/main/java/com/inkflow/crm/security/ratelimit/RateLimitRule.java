package com.inkflow.crm.security.ratelimit;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
public class RateLimitRule {

    private String pattern;
    private String methods = "*";
    private int maxRequests = 60;
    private Duration window = Duration.ofMinutes(1);
}
