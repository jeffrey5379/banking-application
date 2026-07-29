package com.bankapp.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.when;

// Backed by a real ConcurrentHashMap standing in for Redis - see the same rationale in
// backend's IdempotencyStoreTest: pure interaction verification can't express "a ticket
// redeemed once is gone the second time" without a lot of brittle per-key stubbing.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TicketServiceTest {

    @Mock private ReactiveStringRedisTemplate redisTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOperations;

    private final Map<String, String> fakeRedis = new ConcurrentHashMap<>();
    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenAnswer(inv -> {
                    fakeRedis.put(inv.getArgument(0), inv.getArgument(1));
                    return Mono.just(true);
                });
        when(valueOperations.getAndDelete(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> {
                    String value = fakeRedis.remove((String) inv.getArgument(0));
                    return value == null ? Mono.empty() : Mono.just(value);
                });

        ticketService = new TicketService(redisTemplate);
    }

    @Test
    void issueThenConsume_returnsTheSameUserId() {
        UUID userId = UUID.randomUUID();

        String ticket = ticketService.issue(userId).block();

        StepVerifier.create(ticketService.consume(ticket))
                .expectNext(userId)
                .verifyComplete();
    }

    @Test
    void consume_isSingleUse() {
        UUID userId = UUID.randomUUID();
        String ticket = ticketService.issue(userId).block();

        ticketService.consume(ticket).block();

        StepVerifier.create(ticketService.consume(ticket))
                .verifyComplete(); // empty Mono - second redemption finds nothing
    }

    @Test
    void consume_unknownTicket_isEmpty() {
        StepVerifier.create(ticketService.consume("does-not-exist"))
                .verifyComplete();
    }
}
