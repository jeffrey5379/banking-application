package com.bankapp.notification.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

// Pre-auth: wraps the raw bearer token (credentials), principal unset.
// Post-auth: wraps the token's uid claim as the principal
// and its role claim as the sole granted authority
public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String token;
    private final UUID userId;

    public JwtAuthenticationToken(String token) {
        super(List.of());
        this.token = token;
        this.userId = null;
        setAuthenticated(false);
    }

    public JwtAuthenticationToken(UUID userId, String token, String role) {
        super(List.of(new SimpleGrantedAuthority(role)));
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
