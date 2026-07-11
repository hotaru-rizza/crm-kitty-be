package com.inkflow.crm.security.ratelimit;

import com.inkflow.crm.common.http.HttpRequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {

    private final InMemoryRateLimiter rateLimiter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String clientIp = HttpRequestUtils.resolveClientIp(request);
        String path = request.getServletPath();
        String method = request.getMethod();

        if (!rateLimiter.tryConsume(clientIp, method, path)) {
            Duration retryAfter = rateLimiter.retryAfter(clientIp, method, path);
            long retrySeconds = Math.max(1, retryAfter.getSeconds());

            log.warn("Rate limit exceeded: ip={} method={} path={}", clientIp, method, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retrySeconds));
            response.setContentType("application/json");
            response.getWriter().write("""
                    {"success":false,"error":{"code":"RATE_LIMITED","message":"Too many requests. Try again later."}}
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
