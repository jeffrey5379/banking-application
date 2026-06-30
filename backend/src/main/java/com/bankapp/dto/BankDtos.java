package com.bankapp.dto;

import com.bankapp.model.Currency;
import com.bankapp.model.OperationType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class BankDtos {

    // ── Auth DTOs ─────────────────────────────────────────────────────────

    public record RegisterRequest(
            @NotBlank String username,
            @NotBlank String email,
            @NotBlank String password
    ) {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record AuthResponse(
            String token,
            Long userId,
            String username
    ) {}

    // ── Request DTOs ──────────────────────────────────────────────────────

    public record CreateAccountRequest(
            @NotNull Currency currency
    ) {}

    public record MoneyRequest(
            @NotNull @DecimalMin(value = "0.01", message = "Amount must be greater than 0") BigDecimal amount,
            String description
    ) {}

    public record ExchangeRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull Long targetAccountId
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────────────

    public record UserResponse(
            Long id,
            String username,
            String email,
            List<AccountSummaryResponse> accounts
    ) {}

    public record AccountSummaryResponse(
            Long id,
            String accountNumber,
            Currency currency,
            BigDecimal balance,
            Long userId,
            String username
    ) {}

    public record OperationResponse(
            Long id,
            Long accountId,
            String accountNumber,
            OperationType type,
            BigDecimal amount,
            Currency currency,
            BigDecimal balanceAfter,
            String description,
            LocalDateTime createdAt,
            BigDecimal exchangeRate,
            Long relatedAccountId
    ) {}

    public record BalancePoint(
            java.math.BigDecimal balance,
            java.time.LocalDateTime createdAt
    ) {}

    public record OperationPage(
            List<OperationResponse> content,
            int page,
            int size,
            long totalElements,
            boolean last
    ) {}

    public record ExchangeRateResponse(
            Currency from,
            Currency to,
            BigDecimal rate
    ) {}

    public record AccountStatsResponse(
            BigDecimal totalIn,
            BigDecimal totalOut
    ) {}
}
