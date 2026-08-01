package com.bankapp.dto;

import com.bankapp.model.Currency;
import com.bankapp.model.OperationType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class BankDtos {

    // ── Request DTOs ──────────────────────────────────────────────────────

    public record CreateAccountRequest(
            @NotNull Currency currency
    ) {}

    public record ExchangeRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotNull UUID targetAccountId
    ) {}

    public record TransferRequest(
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @NotBlank String targetUsername,
            @NotBlank String targetAccountNumber,
            String description
    ) {}

    public record RecipientCheckResponse(
            boolean valid
    ) {}

    // ── Response DTOs ─────────────────────────────────────────────────────

    public record AccountSummaryResponse(
            UUID id,
            String accountNumber,
            Currency currency,
            BigDecimal balance,
            UUID userId,
            String username
    ) {}

    public record OperationResponse(
            UUID id,
            UUID accountId,
            String accountNumber,
            OperationType type,
            BigDecimal amount,
            Currency currency,
            BigDecimal balanceAfter,
            String description,
            LocalDateTime createdAt,
            BigDecimal exchangeRate,
            UUID relatedAccountId,
            String relatedAccountNumber
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

    public record AccountStatsResponse(
            BigDecimal totalIn,
            BigDecimal totalOut
    ) {}
}
