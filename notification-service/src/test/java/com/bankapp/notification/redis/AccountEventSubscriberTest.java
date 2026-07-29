package com.bankapp.notification.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.UUID;

// Exercises the parse/emit step (handleMessage) directly rather than through a real Redis
// subscription - see the class-level note on why that split exists.
@ExtendWith(MockitoExtension.class)
class AccountEventSubscriberTest {

    @Mock private ReactiveRedisConnectionFactory connectionFactory;

    private AccountEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        subscriber = new AccountEventSubscriber(connectionFactory, objectMapper);
    }

    @Test
    void handleMessage_validJson_isEmittedToSubscribers() {
        UUID ownerId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String json = """
                {"ownerId":"%s","accountId":"%s","accountNumber":"ACC-AAAAAAAA",\
                "type":"TRANSFER_OUT","balanceAfter":900.00,"currency":"EUR",\
                "occurredAt":"2026-07-28T10:15:30"}\
                """.formatted(ownerId, accountId);

        StepVerifier.create(subscriber.events().take(1))
                .then(() -> subscriber.handleMessage(json))
                .assertNext(event -> {
                    assert event.ownerId().equals(ownerId);
                    assert event.accountId().equals(accountId);
                    assert event.balanceAfter().doubleValue() == 900.00;
                })
                .verifyComplete();
    }

    @Test
    void handleMessage_malformedJson_isDiscardedWithoutEmitting() {
        StepVerifier.create(subscriber.events().take(1).timeout(Duration.ofMillis(200)))
                .then(() -> subscriber.handleMessage("not valid json"))
                .verifyError(java.util.concurrent.TimeoutException.class);
    }
}
