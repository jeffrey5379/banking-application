package com.bankapp.notification.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import reactor.core.publisher.Mono;

// Reactive equivalent of backend's JwtAuthFilter/SecurityConfig, adapted to ServerHttpSecurity -
// WebFlux has no OncePerRequestFilter/HttpSecurity, so the servlet filter chain is replaced by an
// AuthenticationWebFilter wired with a reactive converter + authentication manager.
//
// /api/notifications/stream is deliberately NOT covered by this filter: the browser's EventSource
// can't send an Authorization header, so that endpoint authenticates itself via a short-lived
// one-time ticket (see TicketService/NotificationController) instead of a bearer JWT.
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtReactiveAuthenticationManager authenticationManager;
    private final JwtServerAuthenticationConverter authenticationConverter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(authenticationManager);
        jwtFilter.setServerAuthenticationConverter(authenticationConverter);
        jwtFilter.setAuthenticationFailureHandler((webFilterExchange, exception) ->
                Mono.fromRunnable(() ->
                        webFilterExchange.getExchange().getResponse().setStatusCode(HttpStatus.UNAUTHORIZED)));

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(auth -> auth
                        .pathMatchers("/actuator/health", "/api/notifications/stream").permitAll()
                        // Service-to-service only, never routed through the public gateway - see
                        // InternalMessageController.
                        .pathMatchers("/internal/**").permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
