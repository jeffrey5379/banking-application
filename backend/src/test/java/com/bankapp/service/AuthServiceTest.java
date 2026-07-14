package com.bankapp.service;

import com.bankapp.dto.BankDtos.*;
import com.bankapp.security.JwtService;
import com.bankapp.security.TokenBlacklist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserService userService;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private TokenBlacklist tokenBlacklist;

    @InjectMocks
    private AuthService authService;

    private UserDetails aliceDetails;
    private UserResponse aliceResponse;

    @BeforeEach
    void setUp() {
        aliceDetails = User.builder()
                .username("alice")
                .password("$2a$10$hashed")
                .roles("USER")
                .build();

        aliceResponse = new UserResponse(1L, "alice", "alice@example.com", List.of());
    }

    // ── register ──────────────────────────────────────────────────────────

    @Test
    void register_validRequest_encodesPasswordCreatesUserAndReturnsToken() {
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$hashed");
        when(userService.createUser("alice", "alice@example.com", "$2a$10$hashed")).thenReturn(aliceResponse);
        when(userService.loadUserByUsername("alice")).thenReturn(aliceDetails);
        when(jwtService.generateToken(aliceDetails)).thenReturn("jwt.token.here");

        AuthResponse result = authService.register(new RegisterRequest("alice", "alice@example.com", "password123"));

        assertThat(result.token()).isEqualTo("jwt.token.here");
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.username()).isEqualTo("alice");
        verify(passwordEncoder).encode("password123");
        verify(userService).createUser("alice", "alice@example.com", "$2a$10$hashed");
        verify(jwtService).generateToken(aliceDetails);
    }

    @Test
    void register_duplicateUsername_propagatesException() {
        when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashed");
        when(userService.createUser(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Username already exists: alice"));

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "alice@example.com", "password123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alice");

        verify(jwtService, never()).generateToken(any());
    }

    // ── login ─────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_authenticatesAndReturnsToken() {
        when(userService.loadUserByUsername("alice")).thenReturn(aliceDetails);
        when(userService.getUserByUsername("alice")).thenReturn(aliceResponse);
        when(jwtService.generateToken(aliceDetails)).thenReturn("jwt.token.here");

        AuthResponse result = authService.login(new LoginRequest("alice", "password123"));

        assertThat(result.token()).isEqualTo("jwt.token.here");
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.username()).isEqualTo("alice");
        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("alice", "password123"));
    }

    @Test
    void login_badCredentials_propagatesException() {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrongpassword")))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtService, never()).generateToken(any());
    }

    // ── revokeToken ───────────────────────────────────────────────────────

    @Test
    void revokeToken_validToken_addsToBlacklist() {
        long expiryMs = System.currentTimeMillis() + 3_600_000L;
        when(jwtService.extractExpiration("tok")).thenReturn(new Date(expiryMs));

        authService.revokeToken("tok");

        verify(tokenBlacklist).revoke("tok", expiryMs);
    }
}
