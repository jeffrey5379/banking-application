package com.bankapp.notification.security;

import com.bankapp.notification.security.service.ServiceTokenReactiveAuthenticationManager;
import com.bankapp.notification.security.service.ServiceTokenServerAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import reactor.core.publisher.Mono;

// /api/notifications/stream is deliberately NOT covered by this filter: the browser's EventSource
// can't send an Authorization header, so that endpoint authenticates itself via a short-lived
// one-time ticket (see TicketService/NotificationController) instead of a bearer JWT.
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtReactiveAuthenticationManager authenticationManager;
    private final JwtServerAuthenticationConverter authenticationConverter;
    private final ServiceTokenReactiveAuthenticationManager serviceTokenAuthenticationManager;
    private final ServiceTokenServerAuthenticationConverter serviceTokenAuthenticationConverter;

    @Bean
    @Order(1)
    public SecurityWebFilterChain internalServiceWebFilterChain(ServerHttpSecurity http) {
        AuthenticationWebFilter serviceFilter = new AuthenticationWebFilter(serviceTokenAuthenticationManager);
        serviceFilter.setServerAuthenticationConverter(serviceTokenAuthenticationConverter);
        serviceFilter.setAuthenticationFailureHandler((webFilterExchange, exception) ->
                Mono.fromRunnable(() ->
                        webFilterExchange.getExchange().getResponse().setStatusCode(HttpStatus.UNAUTHORIZED)));

        return http
                .securityMatcher(ServerWebExchangeMatchers.pathMatchers("/internal/**"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(auth -> auth.anyExchange().authenticated())
                .addFilterAt(serviceFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    @Bean
    @Order(2)
    public SecurityWebFilterChain apiWebFilterChain(ServerHttpSecurity http) {
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
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }
}
