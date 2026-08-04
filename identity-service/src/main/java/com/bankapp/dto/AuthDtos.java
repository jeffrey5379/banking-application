package com.bankapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank @Email String email,
            @NotBlank
            @Pattern(
                    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{9,}$",
                    message = "Password must be more than 8 characters and include an uppercase letter, "
                            + "a lowercase letter, a digit, and a special character."
            )
            String password
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
            String username,
            boolean admin
    ) {}

    public record UserResponse(
            UUID id,
            String username,
            String email
    ) {}
}
