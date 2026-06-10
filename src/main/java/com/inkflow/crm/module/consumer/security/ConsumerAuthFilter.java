package com.inkflow.crm.module.consumer.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.inkflow.crm.module.consumer.entity.ConsumerUser;
import com.inkflow.crm.module.consumer.repository.ConsumerUserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConsumerAuthFilter extends OncePerRequestFilter {

    private final ConsumerUserRepository consumerUserRepository;

    @Value("${supabase.jwt.issuer}")
    private String jwtIssuer;

    @Value("${supabase.jwt.jwks-uri}")
    private String jwksUri;

    private JwkProvider jwkProvider;

    @PostConstruct
    public void init() {
        try {
            jwkProvider = new JwkProviderBuilder(URI.create(jwksUri).toURL())
                    .cached(10, 24, TimeUnit.HOURS)
                    .rateLimited(10, 1, TimeUnit.MINUTES)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize JwkProvider for URI: " + jwksUri, e);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = extractJwt(request);
        if (!StringUtils.hasText(jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            DecodedJWT decoded = verifyAndDecode(jwt);
            UUID userId = UUID.fromString(decoded.getSubject());
            String email = decoded.getClaim("email").asString();

            ConsumerUser consumer = consumerUserRepository.findById(userId)
                    .orElseGet(() -> {
                        ConsumerUser newUser = new ConsumerUser(userId, email, null);
                        return consumerUserRepository.save(newUser);
                    });

            var auth = new UsernamePasswordAuthenticationToken(
                    consumer,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_CONSUMER"))
            );
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception e) {
            log.warn("Consumer JWT auth failed: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    private DecodedJWT verifyAndDecode(String token) throws Exception {
        DecodedJWT unverified = JWT.decode(token);
        Jwk jwk = jwkProvider.get(unverified.getKeyId());
        ECPublicKey publicKey = (ECPublicKey) jwk.getPublicKey();
        Algorithm algorithm = Algorithm.ECDSA256(publicKey, null);
        return JWT.require(algorithm)
                .withIssuer(jwtIssuer)
                .build()
                .verify(token);
    }

    private String extractJwt(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
