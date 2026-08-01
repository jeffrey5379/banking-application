package com.bankapp.service;

import com.bankapp.model.Account;
import com.bankapp.model.Currency;
import com.bankapp.model.Operation;
import com.bankapp.model.OperationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BalanceEventPublisherTest {

    @Mock private StringRedisTemplate stringRedisTemplate;

    private BalanceEventPublisher publisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new BalanceEventPublisher(stringRedisTemplate, objectMapper);
    }

    @Test
    void publishAfterCommit_noActiveTransaction_publishesImmediately() throws Exception {
        Account account = account();
        Operation operation = operation(account);

        publisher.publishAfterCommit(operation, account);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq(BalanceEventPublisher.CHANNEL), payloadCaptor.capture());

        Map<String, Object> json = objectMapper.readValue(payloadCaptor.getValue(), Map.class);
        assertThat(json.get("ownerId")).isEqualTo(account.getOwnerId().toString());
        assertThat(json.get("accountId")).isEqualTo(account.getPublicId().toString());
        assertThat(json.get("accountNumber")).isEqualTo(account.getAccountNumber());
        assertThat(json.get("type")).isEqualTo("TRANSFER_OUT");
        assertThat(json.get("currency")).isEqualTo("EUR");
        assertThat(json.get("description")).isEqualTo("Transfer to bob (ACC-BBBBBBBB)");
        assertThat(((Number) json.get("amount")).doubleValue()).isEqualTo(100.0);
        assertThat(((Number) json.get("balanceAfter")).doubleValue()).isEqualTo(900.0);
    }

    @Test
    void publishAfterCommit_withActiveTransaction_doesNotPublishUntilCommit() {
        Account account = account();
        Operation operation = operation(account);

        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishAfterCommit(operation, account);

            // Not published yet - only registered to fire on commit.
            verify(stringRedisTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCommit());

            verify(stringRedisTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq(BalanceEventPublisher.CHANNEL), org.mockito.ArgumentMatchers.anyString());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishAfterCommit_neverPublishesOnRollback() {
        Account account = account();
        Operation operation = operation(account);

        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishAfterCommit(operation, account);
            // Simulate a rollback: afterCommit() is simply never invoked in that case.
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(stringRedisTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private Account account() {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "publicId", UUID.randomUUID());
        account.setAccountNumber("ACC-AAAAAAAA");
        account.setCurrency(Currency.EUR);
        account.setBalance(new BigDecimal("900.00"));
        account.setOwnerId(UUID.randomUUID());
        account.setOwnerUsername("alice");
        return account;
    }

    private Operation operation(Account account) {
        Operation operation = new Operation();
        operation.setAccount(account);
        operation.setType(OperationType.TRANSFER_OUT);
        operation.setAmount(new BigDecimal("100.00"));
        operation.setCurrency(account.getCurrency());
        operation.setBalanceAfter(account.getBalance());
        operation.setDescription("Transfer to bob (ACC-BBBBBBBB)");
        operation.setCreatedAt(LocalDateTime.now());
        return operation;
    }
}
