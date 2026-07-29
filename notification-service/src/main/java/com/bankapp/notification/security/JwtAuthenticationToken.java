package com.bankapp.notification.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

// Pre-auth: wraps the raw bearer token (credentials), principal unset.
// Post-auth: wraps the token's uid claim as the principal - this service never needs anything
// else off the token (no username, no DB-backed UserDetails, unlike backend/identity-service).
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String token;
    private final UUID userId;

    public JwtAuthenticationToken(String token) {
        super(List.of());
        this.token = token;
        this.userId = null;
        setAuthenticated(false);
    }

    public JwtAuthenticationToken(UUID userId, String token) {
        super(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.token = token;
        this.userId = userId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }
}
