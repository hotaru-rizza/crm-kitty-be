package com.inkflow.crm.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.inkflow.crm.domain.enums.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${supabase.jwt.secret}")
    private String jwtSecret;

    @Value("${supabase.jwt.issuer}")
    private String jwtIssuer;

    private JWTVerifier verifier;

    @PostConstruct
    public void init() {
        Algorithm algorithm = Algorithm.HMAC256(jwtSecret);
        verifier = JWT.require(algorithm)
                .withIssuer(jwtIssuer)
                .build();
    }

    public boolean validateToken(String token) {
        try {
            verifier.verify(token);
            return true;
        } catch (JWTVerificationException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public UserPrincipal getUserPrincipal(String token) {
        DecodedJWT jwt = verifier.verify(token);

        UUID userId = UUID.fromString(jwt.getSubject());
        String email = jwt.getClaim("email").asString();
        UUID tenantId = UUID.fromString(jwt.getClaim("tenant_id").asString());
        String roleStr = jwt.getClaim("role").asString();
        UserRole role = UserRole.fromValue(roleStr);

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
}
