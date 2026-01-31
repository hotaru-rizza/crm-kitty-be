package com.inkflow.crm.security;

import com.inkflow.crm.domain.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String email;
    private final UUID tenantId;
    private final UserRole role;
    private final List<UUID> locationIds;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public boolean isOwner() {
        return role == UserRole.OWNER;
    }

    public boolean isAdmin() {
        return role == UserRole.OWNER || role == UserRole.ADMIN;
    }

    public boolean isArtist() {
        return role == UserRole.ARTIST;
    }

    public boolean hasAccessToLocation(UUID locationId) {
        if (isAdmin()) {
            return true;
        }
        return locationIds != null && locationIds.contains(locationId);
    }
}
