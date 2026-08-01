package com.bankapp.notification.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.BadCredentialsException;
import reactor.test.StepVerifier;

import java.util.Optional;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceTokenReactiveAuthenticationManagerTest {

    @Mock
    private ServiceTokenAuthenticator authenticator;

    private ServiceTokenReactiveAuthenticationManager manager;

    @BeforeEach
    void setUp() {
        manager = new ServiceTokenReactiveAuthenticationManager(authenticator);
    }

    @Test
    void authenticate_validToken_emitsAuthenticatedServiceToken() {
        when(authenticator.verify("good-token")).thenReturn(Optional.of("core-banking"));

        StepVerifier.create(manager.authenticate(new ServiceAuthenticationToken("good-token")))
                .assertNext(auth -> {
                    ServiceAuthenticationToken result = (ServiceAuthenticationToken) auth;
                    org.assertj.core.api.Assertions.assertThat(result.isAuthenticated()).isTrue();
                    org.assertj.core.api.Assertions.assertThat(result.getPrincipal()).isEqualTo("core-banking");
                })
                .verifyComplete();
    }

    @Test
    void authenticate_invalidToken_errorsWithBadCredentials() {
        when(authenticator.verify("bad-token")).thenReturn(Optional.empty());

        StepVerifier.create(manager.authenticate(new ServiceAuthenticationToken("bad-token")))
                .expectError(BadCredentialsException.class)
                .verify();
    }
}
