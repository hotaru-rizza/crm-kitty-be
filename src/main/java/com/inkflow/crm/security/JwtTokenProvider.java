package com.inkflow.crm.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.StaffRepository;
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

    private final StaffRepository staffRepository;
    private JwkProvider jwkProvider;

    public JwtTokenProvider(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

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
            logInvalidToken(token, e);
            return false;
        }
    }

    private void logInvalidToken(String token, Exception e) {
        try {
            DecodedJWT decoded = JWT.decode(token);
            log.error("Invalid JWT token: {} | alg={} kid={} iss={} expectedIss={} sub={}",
                    e.getMessage(), decoded.getAlgorithm(), decoded.getKeyId(),
                    decoded.getIssuer(), jwtIssuer, decoded.getSubject());
        } catch (Exception decodeError) {
            log.error("Invalid JWT token (undecodable): {}", e.getMessage());
        }
    }

    public DecodedJWT verifyToken(String token) {
        return verifyAndDecode(token);
    }

    public UserPrincipal getUserPrincipal(String token) {
        DecodedJWT jwt = verifyAndDecode(token);

        String supabaseUserId = jwt.getSubject();
        String email = jwt.getClaim("email").asString();

        String tenantIdStr = jwt.getClaim("tenant_id").asString();
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : null;

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

        UUID staffId = null;
        var staffOpt = staffRepository.findByAuthUserIdAndDeletedAtIsNull(supabaseUserId);
        if (staffOpt.isPresent()) {
            Staff staff = staffOpt.get();
            staffId = staff.getId();
            if (tenantId == null) {
                tenantId = staff.getTenantId();
            }
            if (role == null) {
                role = staff.getRole();
            }
            if (locationIds.isEmpty() && staff.getLocations() != null) {
                locationIds = staff.getLocations().stream()
                        .map(loc -> loc.getId())
                        .toList();
            }
            if (email == null) {
                email = staff.getEmail();
            }
        } else {
            log.warn("No staff record found for auth user {}", supabaseUserId);
        }

        return UserPrincipal.builder()
                .id(staffId != null ? staffId : UUID.fromString(supabaseUserId))
                .authUserId(supabaseUserId)
                .email(email)
                .tenantId(tenantId)
                .role(role)
                .locationIds(locationIds)
                .sessionId(resolveSessionId(jwt))
                .build();
    }

    private String resolveSessionId(DecodedJWT jwt) {
        String sessionId = jwt.getClaim("session_id").asString();
        if (sessionId != null && !sessionId.isBlank()) {
            return sessionId;
        }

        String tokenId = jwt.getId();
        if (tokenId != null && !tokenId.isBlank()) {
            return tokenId;
        }

        if (jwt.getIssuedAt() != null) {
            return jwt.getSubject() + ":" + jwt.getIssuedAt().getTime();
        }

        return null;
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
