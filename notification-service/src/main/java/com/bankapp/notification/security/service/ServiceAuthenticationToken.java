package com.bankapp.notification.security.service;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

// Pre-auth: wraps the raw X-Service-Token (credentials), principal unset.
// Post-auth: wraps the verified caller's service name as the principal - see
// ServiceTokenReactiveAuthenticationManager.
public class ServiceAuthenticationToken extends AbstractAuthenticationToken {

    private final String token;
    private final String issuer;

    public ServiceAuthenticationToken(String token) {
        super(List.of());
        this.token = token;
        this.issuer = null;
        setAuthenticated(false);
    }

    public ServiceAuthenticationToken(String issuer, String token) {
        super(List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")));
        this.token = token;
        this.issuer = issuer;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return issuer;
    }
}
