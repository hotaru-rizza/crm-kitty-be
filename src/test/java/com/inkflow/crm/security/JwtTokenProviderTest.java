package com.inkflow.crm.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.inkflow.crm.domain.entity.Location;
import com.inkflow.crm.domain.entity.Staff;
import com.inkflow.crm.domain.enums.UserRole;
import com.inkflow.crm.domain.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtTokenProviderTest {

    private static final String JWT_ISSUER = "https://test.supabase.co/auth/v1";
    private static final String KEY_ID = "test-key-id";

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private JwkProvider jwkProvider;

    private JwtTokenProvider tokenProvider;
    private ECPrivateKey privateKey;
    private ECPublicKey publicKey;
    private Algorithm signingAlgorithm;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        keyGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = keyGen.generateKeyPair();
        privateKey = (ECPrivateKey) keyPair.getPrivate();
        publicKey = (ECPublicKey) keyPair.getPublic();
        signingAlgorithm = Algorithm.ECDSA256(publicKey, privateKey);

        tokenProvider = new JwtTokenProvider(staffRepository);
        ReflectionTestUtils.setField(tokenProvider, "jwtIssuer", JWT_ISSUER);
        ReflectionTestUtils.setField(tokenProvider, "jwksUri", JWT_ISSUER + "/.well-known/jwks.json");
        ReflectionTestUtils.setField(tokenProvider, "jwkProvider", jwkProvider);

        Jwk jwk = mock(Jwk.class);
        lenient().when(jwk.getPublicKey()).thenReturn(publicKey);
        lenient().when(jwkProvider.get(KEY_ID)).thenReturn(jwk);
    }

    @Test
    void shouldReturnTrueWhenValidateTokenAndSignatureValid() {
        String token = buildToken(b -> b.withSubject(UUID.randomUUID().toString()));

        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void shouldReturnFalseWhenValidateTokenAndTokenExpired() {
        String token = buildToken(b -> b
                .withSubject(UUID.randomUUID().toString())
                .withExpiresAt(new Date(System.currentTimeMillis() - 60_000)));

        assertFalse(tokenProvider.validateToken(token));
    }

    @Test
    void shouldReturnFalseWhenValidateTokenAndSignatureInvalid() throws Exception {
        KeyPairGenerator otherKeyGen = KeyPairGenerator.getInstance("EC");
        otherKeyGen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair otherKeyPair = otherKeyGen.generateKeyPair();
        Algorithm otherAlgorithm = Algorithm.ECDSA256(
                (ECPublicKey) otherKeyPair.getPublic(),
                (ECPrivateKey) otherKeyPair.getPrivate()
        );

        String token = JWT.create()
                .withKeyId(KEY_ID)
                .withIssuer(JWT_ISSUER)
                .withSubject(UUID.randomUUID().toString())
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000))
                .sign(otherAlgorithm);

        assertFalse(tokenProvider.validateToken(token));
    }

    @Test
    void shouldReturnFalseWhenValidateTokenAndJwkFetchFails() throws JwkException {
        when(jwkProvider.get(KEY_ID)).thenThrow(new JwkException("JWK not found"));

        String token = buildToken(b -> b.withSubject(UUID.randomUUID().toString()));

        assertFalse(tokenProvider.validateToken(token));
    }

    @Test
    void shouldReturnFalseWhenValidateTokenAndIssuerMismatch() {
        String token = buildToken(b -> b
                .withSubject(UUID.randomUUID().toString())
                .withIssuer("https://wrong-issuer.example/auth/v1"));

        assertFalse(tokenProvider.validateToken(token));
    }

    @Test
    void shouldVerifyTokenWhenSignatureValid() {
        String authUserId = UUID.randomUUID().toString();
        String token = buildToken(b -> b
                .withSubject(authUserId)
                .withClaim("email", "staff@test.com"));

        DecodedJWT decoded = tokenProvider.verifyToken(token);

        assertEquals(authUserId, decoded.getSubject());
        assertEquals("staff@test.com", decoded.getClaim("email").asString());
    }

    @Test
    void shouldThrowWhenVerifyTokenAndTokenExpired() {
        String token = buildToken(b -> b
                .withSubject(UUID.randomUUID().toString())
                .withExpiresAt(new Date(System.currentTimeMillis() - 60_000)));

        assertThrows(JWTVerificationException.class, () -> tokenProvider.verifyToken(token));
    }

    @Test
    void shouldBuildUserPrincipalFromJwtClaimsWhenStaffExists() {
        String authUserId = UUID.randomUUID().toString();
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .email("staff@test.com")
                .role(UserRole.ADMIN)
                .build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.of(staff));

        String token = buildToken(b -> b
                .withSubject(authUserId)
                .withClaim("email", "jwt@test.com")
                .withClaim("tenant_id", tenantId.toString())
                .withClaim("user_role", "admin")
                .withClaim("location_ids", List.of(locationId.toString())));

        UserPrincipal principal = tokenProvider.getUserPrincipal(token);

        assertEquals(staffId, principal.getId());
        assertEquals(authUserId, principal.getAuthUserId());
        assertEquals("jwt@test.com", principal.getEmail());
        assertEquals(tenantId, principal.getTenantId());
        assertEquals(UserRole.ADMIN, principal.getRole());
        assertEquals(List.of(locationId), principal.getLocationIds());
        verify(staffRepository).findByAuthUserIdAndDeletedAtIsNull(authUserId);
    }

    @Test
    void shouldExtractTenantIdFromJwtClaim() {
        String authUserId = UUID.randomUUID().toString();
        UUID tenantId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.empty());

        String token = buildToken(b -> b
                .withSubject(authUserId)
                .withClaim("tenant_id", tenantId.toString()));

        UserPrincipal principal = tokenProvider.getUserPrincipal(token);

        assertEquals(tenantId, principal.getTenantId());
    }

    @Test
    void shouldResolveTenantRoleAndLocationsFromStaffWhenMissingInJwt() {
        String authUserId = UUID.randomUUID().toString();
        UUID tenantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();

        Location location = Location.builder().id(locationId).tenantId(tenantId).build();
        Staff staff = Staff.builder()
                .id(staffId)
                .tenantId(tenantId)
                .email("staff@test.com")
                .role(UserRole.ARTIST)
                .locations(Set.of(location))
                .build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.of(staff));

        String token = buildToken(b -> b.withSubject(authUserId));

        UserPrincipal principal = tokenProvider.getUserPrincipal(token);

        assertEquals(staffId, principal.getId());
        assertEquals(tenantId, principal.getTenantId());
        assertEquals(UserRole.ARTIST, principal.getRole());
        assertEquals("staff@test.com", principal.getEmail());
        assertEquals(List.of(locationId), principal.getLocationIds());
    }

    @Test
    void shouldUseSupabaseUserIdWhenStaffRecordMissing() {
        String authUserId = UUID.randomUUID().toString();
        UUID tenantId = UUID.randomUUID();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.empty());

        String token = buildToken(b -> b
                .withSubject(authUserId)
                .withClaim("tenant_id", tenantId.toString())
                .withClaim("user_role", "owner"));

        UserPrincipal principal = tokenProvider.getUserPrincipal(token);

        assertEquals(UUID.fromString(authUserId), principal.getId());
        assertEquals(authUserId, principal.getAuthUserId());
        assertEquals(tenantId, principal.getTenantId());
        assertEquals(UserRole.OWNER, principal.getRole());
        assertTrue(principal.getLocationIds().isEmpty());
    }

    @Test
    void shouldIgnoreUnknownUserRoleClaim() {
        String authUserId = UUID.randomUUID().toString();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.empty());

        String token = buildToken(b -> b
                .withSubject(authUserId)
                .withClaim("user_role", "unknown-role"));

        UserPrincipal principal = tokenProvider.getUserPrincipal(token);

        assertNull(principal.getRole());
        assertEquals("ROLE_AUTHENTICATED", principal.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void shouldPreferJwtTenantIdOverStaffTenantId() {
        String authUserId = UUID.randomUUID().toString();
        UUID jwtTenantId = UUID.randomUUID();
        UUID staffTenantId = UUID.randomUUID();

        Staff staff = Staff.builder()
                .id(UUID.randomUUID())
                .tenantId(staffTenantId)
                .email("staff@test.com")
                .role(UserRole.ADMIN)
                .build();

        when(staffRepository.findByAuthUserIdAndDeletedAtIsNull(authUserId)).thenReturn(Optional.of(staff));

        String token = buildToken(b -> b
                .withSubject(authUserId)
                .withClaim("tenant_id", jwtTenantId.toString()));

        UserPrincipal principal = tokenProvider.getUserPrincipal(token);

        assertEquals(jwtTenantId, principal.getTenantId());
    }

    private String buildToken(Consumer<com.auth0.jwt.JWTCreator.Builder> customizer) {
        com.auth0.jwt.JWTCreator.Builder builder = JWT.create()
                .withKeyId(KEY_ID)
                .withIssuer(JWT_ISSUER)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + 3_600_000));
        customizer.accept(builder);
        return builder.sign(signingAlgorithm);
    }
}
