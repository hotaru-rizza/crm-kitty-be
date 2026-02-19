package com.inkflow.crm.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.inkflow.crm.domain.enums.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.interfaces.ECPublicKey;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${supabase.jwt.issuer}")
    private String jwtIssuer;

    @Value("${supabase.jwt.jwks-uri}")
    private String jwksUri;

    private JwkProvider jwkProvider;

    @PostConstruct
    public void init() throws MalformedURLException {
        jwkProvider = new JwkProviderBuilder(URI.create(jwksUri).toURL())
                .cached(10, 24, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build();
    }

    public boolean validateToken(String token) {
        try {
            verifyAndDecode(token);
            return true;
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public UserPrincipal getUserPrincipal(String token) {
        DecodedJWT jwt = verifyAndDecode(token);

        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaim("email").asString();

        // tenant_id and user_role are stored in app_metadata → appear as top-level JWT claims
        String tenantIdStr = jwt.getClaim("tenant_id").asString();
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;

        // Supabase sets "role" = "authenticated" (system claim); our role is in "user_role"
        String roleStr = jwt.getClaim("user_role").asString();
        UserRole role = null;
        if (roleStr != null) {
            try {
                role = UserRole.fromValue(roleStr);
            } catch (IllegalArgumentException ignored) {
            }
        }

        List<UUID> locationIds = Collections.emptyList();
        List<String> locationIdStrings = jwt.getClaim("location_ids").asList(String.class);
        if (locationIdStrings != null) {
            locationIds = locationIdStrings.stream()
                    .map(UUID::fromString)
                    .toList();
        }

        return UserPrincipal.builder()
                .id(userId)
                .email(email)
                .tenantId(tenantId)
                .role(role)
                .locationIds(locationIds)
                .build();
    }

    private DecodedJWT verifyAndDecode(String token) {
        try {
            DecodedJWT unverified = JWT.decode(token);
            String kid = unverified.getKeyId();

            Jwk jwk = jwkProvider.get(kid);
            ECPublicKey publicKey = (ECPublicKey) jwk.getPublicKey();

            Algorithm algorithm = Algorithm.ECDSA256(publicKey, null);
            return JWT.require(algorithm)
                    .withIssuer(jwtIssuer)
                    .build()
                    .verify(token);
        } catch (JwkException e) {
            throw new JWTVerificationException("Failed to fetch JWK: " + e.getMessage(), e);
        }
    }
}
