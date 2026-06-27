package com.inkflow.crm.security;

import com.inkflow.crm.common.logging.MdcKeys;
import io.sentry.Sentry;
import io.sentry.protocol.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("!dev")
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                if (tokenProvider.validateToken(jwt)) {
                    UserPrincipal userPrincipal = tokenProvider.getUserPrincipal(jwt);
                    log.info("JWT auth OK: user={}, tenant={}, role={}, authUserId={}",
                            userPrincipal.getId(), userPrincipal.getTenantId(),
                            userPrincipal.getRole(), userPrincipal.getAuthUserId());

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userPrincipal,
                                    null,
                                    userPrincipal.getAuthorities()
                            );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    TenantContext.setCurrentTenant(userPrincipal.getTenantId());
                    TenantContext.setCurrentUser(userPrincipal.getId());
                    TenantContext.setCurrentRole(userPrincipal.getRole());
                    TenantContext.setCurrentLocationIds(userPrincipal.getLocationIds());

                    MDC.put(MdcKeys.TENANT_ID, userPrincipal.getTenantId().toString());
                    MDC.put(MdcKeys.USER_ID, userPrincipal.getId().toString());
                    configureSentryScope(userPrincipal);
                } else {
                    log.warn("JWT validation FAILED for request {} {}", request.getMethod(), request.getRequestURI());
                }
            } else {
                log.debug("No JWT token in request {} {}", request.getMethod(), request.getRequestURI());
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context: {}", ex.getMessage(), ex);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void configureSentryScope(UserPrincipal principal) {
        if (!Sentry.isEnabled()) {
            return;
        }

        User user = new User();
        user.setId(principal.getId().toString());
        user.setEmail(principal.getEmail());
        Sentry.setUser(user);
        Sentry.configureScope(scope -> {
            scope.setTag("tenantId", principal.getTenantId().toString());
            scope.setTag("userId", principal.getId().toString());
            scope.setTag("role", principal.getRole().name());
        });
    }
}
