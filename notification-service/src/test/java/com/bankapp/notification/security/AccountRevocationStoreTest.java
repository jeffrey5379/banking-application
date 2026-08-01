package com.bankapp.notification.security;

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

import java.util.Date;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountRevocationStoreTest {

    @Mock private ReactiveStringRedisTemplate redisTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOperations;

    private AccountRevocationStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new AccountRevocationStore(redisTemplate);
    }

    @Test
    void isRevoked_noMarkerStored_returnsFalse() {
        when(valueOperations.get("revoked-since:alice")).thenReturn(Mono.empty());

        StepVerifier.create(store.isRevoked("alice", new Date()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isRevoked_tokenIssuedBeforeRevocation_returnsTrue() {
        long revokedAt = System.currentTimeMillis();
        when(valueOperations.get("revoked-since:alice")).thenReturn(Mono.just(String.valueOf(revokedAt)));

        StepVerifier.create(store.isRevoked("alice", new Date(revokedAt - 1000)))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isRevoked_tokenIssuedAtExactRevocationInstant_returnsTrue() {
        long revokedAt = System.currentTimeMillis();
        when(valueOperations.get("revoked-since:alice")).thenReturn(Mono.just(String.valueOf(revokedAt)));

        StepVerifier.create(store.isRevoked("alice", new Date(revokedAt)))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isRevoked_tokenIssuedAfterRevocation_returnsFalse() {
        long revokedAt = System.currentTimeMillis();
        when(valueOperations.get("revoked-since:alice")).thenReturn(Mono.just(String.valueOf(revokedAt)));

        StepVerifier.create(store.isRevoked("alice", new Date(revokedAt + 1000)))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isRevoked_differentUsername_notAffectedByAnotherUsersRevocation() {
        when(valueOperations.get("revoked-since:bob")).thenReturn(Mono.empty());

        StepVerifier.create(store.isRevoked("bob", new Date()))
                .expectNext(false)
                .verifyComplete();
    }
}
