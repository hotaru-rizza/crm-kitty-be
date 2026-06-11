package com.inkflow.crm.security;

import com.inkflow.crm.domain.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserPrincipalTest {

    @Test
    void shouldExposeRoleBasedAuthorities() {
        UserPrincipal owner = principal(UserRole.OWNER);
        UserPrincipal artist = principal(UserRole.ARTIST);

        assertEquals("ROLE_OWNER", owner.getAuthorities().iterator().next().getAuthority());
        assertEquals("ROLE_ARTIST", artist.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void shouldUseAuthenticatedAuthorityWhenRoleMissing() {
        UserPrincipal principal = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .email("unknown@test.com")
                .build();

        assertEquals("ROLE_AUTHENTICATED", principal.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void shouldIdentifyOwnerAdminAndArtistRoles() {
        UserPrincipal owner = principal(UserRole.OWNER);
        UserPrincipal admin = principal(UserRole.ADMIN);
        UserPrincipal artist = principal(UserRole.ARTIST);

        assertTrue(owner.isOwner());
        assertTrue(owner.isAdmin());
        assertFalse(owner.isArtist());

        assertFalse(admin.isOwner());
        assertTrue(admin.isAdmin());
        assertFalse(admin.isArtist());

        assertFalse(artist.isOwner());
        assertFalse(artist.isAdmin());
        assertTrue(artist.isArtist());
    }

    @Test
    void shouldGrantAdminAccessToAnyLocation() {
        UUID locationId = UUID.randomUUID();
        UserPrincipal admin = principal(UserRole.ADMIN);

        assertTrue(admin.hasAccessToLocation(locationId));
    }

    @Test
    void shouldRestrictArtistToAssignedLocations() {
        UUID allowed = UUID.randomUUID();
        UUID denied = UUID.randomUUID();
        UserPrincipal artist = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .role(UserRole.ARTIST)
                .locationIds(List.of(allowed))
                .build();

        assertTrue(artist.hasAccessToLocation(allowed));
        assertFalse(artist.hasAccessToLocation(denied));
    }

    @Test
    void shouldDenyArtistWhenLocationIdsMissing() {
        UserPrincipal artist = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .role(UserRole.ARTIST)
                .build();

        assertFalse(artist.hasAccessToLocation(UUID.randomUUID()));
    }

    private UserPrincipal principal(UserRole role) {
        return UserPrincipal.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .role(role)
                .email("user@test.com")
                .build();
    }
}
