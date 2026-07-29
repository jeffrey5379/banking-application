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
// subscription - mirrors AccountEventSubscriberTest.
@ExtendWith(MockitoExtension.class)
class MessageEventSubscriberTest {

    @Mock private ReactiveRedisConnectionFactory connectionFactory;

    private MessageEventSubscriber subscriber;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        subscriber = new MessageEventSubscriber(connectionFactory, objectMapper);
    }

    @Test
    void handleMessage_validJson_isEmittedToSubscribers() {
        UUID ownerId = UUID.randomUUID();
        String json = """
                {"ownerId":"%s","id":"msg-1","subject":"Suspicious login attempt detected",\
                "body":"We detected a login attempt from a new device.",\
                "receivedAt":"2026-07-28T10:15:30Z","priority":"HIGH"}\
                """.formatted(ownerId);

        StepVerifier.create(subscriber.events().take(1))
                .then(() -> subscriber.handleMessage(json))
                .assertNext(event -> {
                    assert event.ownerId().equals(ownerId);
                    assert event.id().equals("msg-1");
                    assert event.subject().equals("Suspicious login attempt detected");
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
