package com.bankapp.service;

import com.bankapp.model.Account;
import com.bankapp.model.Operation;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Publishes a balance-change event for notification-service to relay to the owner's browser over
 * SSE. Backed by Redis Pub/Sub (same shared instance every other service already uses) rather than
 * a direct call to notification-service - core-banking shouldn't need to know or care whether
 * anyone is actually listening.
 *
 * Published as plain JSON via StringRedisTemplate rather than the app's general-purpose
 * RedisTemplate<String, Object> (see RedisConfig/IdempotencyStore) - that one's
 * GenericJackson2JsonRedisSerializer embeds an "@class" field naming this exact Java type, which
 * only this service can resolve. A cross-service wire contract needs a plain, self-contained
 * payload instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceEventPublisher {

    public static final String CHANNEL = "account-events";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public record AccountEvent(
            UUID ownerId,
            UUID accountId,
            String accountNumber,
            String type,
            java.math.BigDecimal amount,
            java.math.BigDecimal balanceAfter,
            String currency,
            String description,
            LocalDateTime occurredAt
    ) implements Serializable {}

    // Fires only after the surrounding transaction actually commits - publishing eagerly would
    // notify a browser about a balance change that a later failure in the same transaction rolls
    // back (e.g. a subsequent Operation save fails), leaving the client with a phantom update.
    public void publishAfterCommit(Operation operation, Account account) {
        AccountEvent event = new AccountEvent(
                account.getOwnerId(),
                account.getPublicId(),
                account.getAccountNumber(),
                operation.getType().name(),
                operation.getAmount(),
                operation.getBalanceAfter(),
                account.getCurrency().name(),
                operation.getDescription(),
                operation.getCreatedAt()
        );

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(event);
            }
        });
    }

    private void publish(AccountEvent event) {
        try {
            stringRedisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            // Best-effort: a live-update push failing must never affect the transfer it describes,
            // which has already committed by the time this runs.
            log.warn("Failed to publish account event for account {}", event.accountId(), e);
        }
    }
}
