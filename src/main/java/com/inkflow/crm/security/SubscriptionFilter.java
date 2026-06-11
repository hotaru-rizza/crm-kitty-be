package com.inkflow.crm.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkflow.crm.module.subscription.service.SubscriptionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionFilter extends OncePerRequestFilter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> ALLOWED_PREFIXES = Set.of(
            "/api/subscription", "/api/payments", "/api/onboarding",
            "/api/public", "/api/actuator", "/api/staff/accept-invite",
            "/api/staff/invite/info", "/api/requests"
    );

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String method = request.getMethod();
        String uri    = request.getRequestURI();


        if (!WRITE_METHODS.contains(method)) {
            chain.doFilter(request, response);
            return;
        }


        for (String prefix : ALLOWED_PREFIXES) {
            if (uri.startsWith(prefix)) {
                chain.doFilter(request, response);
                return;
            }
        }


        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            chain.doFilter(request, response);
            return;
        }


        try {
            UUID tenantId = SecurityUtils.getCurrentTenantId();
            if (tenantId != null && !subscriptionService.isSubscriptionActive(tenantId)) {
                log.warn("Subscription expired for tenant {}, blocking {} {}", tenantId, method, uri);
                response.setStatus(402);
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> body = Map.of(
                        "error", "SUBSCRIPTION_EXPIRED",
                        "message", "Ваш пробний період або підписка завершилися. Оновіть підписку для продовження роботи.",
                        "code", 402
                );
                response.getWriter().write(objectMapper.writeValueAsString(body));
                return;
            }
        } catch (Exception e) {

            log.warn("Could not check subscription status: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}
