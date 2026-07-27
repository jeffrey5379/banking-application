package com.bankapp.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

// Built directly from a validated JWT's claims - core-banking has no local Users table, unlike
// identity-service's UserDetailsService this never touches a database.
@Getter
public class JwtPrincipal implements UserDetails {

    private final String username;
    private final UUID userId;

    public JwtPrincipal(String username, UUID userId) {
        this.username = username;
        this.userId = userId;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
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
}
