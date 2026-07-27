package com.bankapp.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank String email,
            @NotBlank String password
    ) {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record LoginChallengeResponse(
            String challengeToken
    ) {}

    public record VerifyOtpRequest(
            @NotBlank String challengeToken,
            @NotBlank String code
    ) {}

    public record AuthResponse(
            String token,
            UUID userId,
            String username
    ) {}

    public record UserResponse(
            UUID id,
            String username,
            String email
    ) {}
}
