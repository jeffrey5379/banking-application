package com.bankapp.security.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

// Guards /internal/** (see the dedicated internalServiceFilterChain in SecurityConfig): requires
// a valid X-Service-Token minted by one of the trusted callers configured in
// ServiceTokenKeyLocator, replacing the permitAll() this path used to have.
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Service-Token";

    private final ServiceTokenAuthenticator authenticator;

    public InternalServiceAuthFilter(ServiceTokenAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader(HEADER);
        if (token != null) {
            Optional<String> issuer = authenticator.verify(token);
            issuer.ifPresent(name -> SecurityContextHolder.getContext().setAuthentication(
                    new ServiceAuthenticationToken(name)));
        }
        filterChain.doFilter(request, response);
    }

    private static class ServiceAuthenticationToken extends AbstractAuthenticationToken {
        private final String issuer;

        ServiceAuthenticationToken(String issuer) {
            super(List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE")));
            this.issuer = issuer;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return issuer;
        }
    }
}
