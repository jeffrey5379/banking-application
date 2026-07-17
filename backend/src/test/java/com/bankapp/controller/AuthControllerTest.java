package com.bankapp.controller;

import com.bankapp.dto.BankDtos.*;
import com.bankapp.exception.GlobalExceptionHandler;
import com.bankapp.exception.InvalidOtpException;
import com.bankapp.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final UUID ALICE_PUBLIC_ID = UUID.randomUUID();

    // ── POST /api/auth/register ───────────────────────────────────────────

    @Test
    void register_validRequest_returns201WithToken() throws Exception {
        AuthResponse authResponse = new AuthResponse("jwt.token.here", ALICE_PUBLIC_ID, "alice");
        when(authService.register(any())).thenReturn(authResponse);

        RegisterRequest req = new RegisterRequest("alice", "alice@example.com", "alice123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.userId").value(ALICE_PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void register_duplicateUsername_returns400() throws Exception {
        when(authService.register(any()))
                .thenThrow(new IllegalArgumentException("Username already exists: alice"));

        RegisterRequest req = new RegisterRequest("alice", "alice@example.com", "alice123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_blankUsername_returns400() throws Exception {
        RegisterRequest req = new RegisterRequest("", "alice@example.com", "alice123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200WithChallengeToken() throws Exception {
        LoginChallengeResponse challenge = new LoginChallengeResponse("challenge-token-1");
        when(authService.login(any())).thenReturn(challenge);

        LoginRequest req = new LoginRequest("alice", "alice123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeToken").value("challenge-token-1"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest req = new LoginRequest("alice", "wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        LoginRequest req = new LoginRequest("alice", "");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/verify-otp ───────────────────────────────────────────

    @Test
    void verifyOtp_validCode_returns200WithToken() throws Exception {
        AuthResponse authResponse = new AuthResponse("jwt.token.here", ALICE_PUBLIC_ID, "alice");
        when(authService.verifyOtp(any())).thenReturn(authResponse);

        VerifyOtpRequest req = new VerifyOtpRequest("challenge-token-1", "111111");

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.token.here"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void verifyOtp_invalidCode_returns401() throws Exception {
        when(authService.verifyOtp(any())).thenThrow(new InvalidOtpException("Invalid code"));

        VerifyOtpRequest req = new VerifyOtpRequest("challenge-token-1", "000000");

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyOtp_blankCode_returns400() throws Exception {
        VerifyOtpRequest req = new VerifyOtpRequest("challenge-token-1", "");

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
}
