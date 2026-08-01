package com.bankapp.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AccountRevocationStore accountRevocationStore;

    public JwtAuthFilter(JwtService jwtService, AccountRevocationStore accountRevocationStore) {
        this.jwtService = jwtService;
        this.accountRevocationStore = accountRevocationStore;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null && jwtService.isTokenValid(token)) {
                String username = jwtService.extractUsername(token);
                if (!accountRevocationStore.isRevoked(username, jwtService.extractIssuedAt(token))) {
                    UUID userId = jwtService.extractUserId(token);
                    JwtPrincipal principal = new JwtPrincipal(username, userId);
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception ignored) {
            // invalid, expired, or malformed token — Spring Security returns 401 via entry point
        }
        filterChain.doFilter(request, response);
    }
}
