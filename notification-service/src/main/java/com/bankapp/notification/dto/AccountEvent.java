package com.bankapp.notification.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Mirrors the JSON payload core-banking publishes on the "account-events" Redis channel (see
// backend's BalanceEventPublisher.AccountEvent) - deliberately a plain, self-contained shape
// rather than a shared library type, since these are two independently deployable services.
public record AccountEvent(
        UUID ownerId,
        UUID accountId,
        String accountNumber,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String currency,
        String description,
        LocalDateTime occurredAt
) {}
