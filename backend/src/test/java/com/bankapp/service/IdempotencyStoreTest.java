package com.bankapp.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyStoreTest {

    private final IdempotencyStore store = new IdempotencyStore();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nullKey_alwaysInvokesAction() {
        AtomicInteger calls = new AtomicInteger();
        store.execute(null, "credit:1", () -> calls.incrementAndGet());
        store.execute(null, "credit:1", () -> calls.incrementAndGet());

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void blankKey_alwaysInvokesAction() {
        AtomicInteger calls = new AtomicInteger();
        store.execute("", "credit:1", () -> calls.incrementAndGet());
        store.execute("   ", "credit:1", () -> calls.incrementAndGet());

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void repeatedKey_invokesActionOnceAndReplaysResult() {
        AtomicInteger calls = new AtomicInteger();
        Integer first = store.execute("key-1", "credit:1", calls::incrementAndGet);
        Integer second = store.execute("key-1", "credit:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
    }

    @Test
    void differentKeys_bothInvokeAction() {
        AtomicInteger calls = new AtomicInteger();
        store.execute("key-1", "credit:1", calls::incrementAndGet);
        store.execute("key-2", "credit:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void sameKeyDifferentOperation_bothInvokeAction() {
        AtomicInteger calls = new AtomicInteger();
        store.execute("key-1", "credit:1", calls::incrementAndGet);
        store.execute("key-1", "debit:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void sameKeyDifferentUser_bothInvokeAction() {
        AtomicInteger calls = new AtomicInteger();
        store.execute("key-1", "credit:1", calls::incrementAndGet);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("bob", null));
        store.execute("key-1", "credit:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void failedAttempt_isNotCached_andCanBeRetried() {
        AtomicInteger calls = new AtomicInteger();
        try {
            store.execute("key-1", "credit:1", () -> {
                calls.incrementAndGet();
                throw new IllegalStateException("insufficient funds");
            });
        } catch (IllegalStateException ignored) {
            // expected
        }

        Integer result = store.execute("key-1", "credit:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(result).isEqualTo(2);
    }

    @Test
    void expiredEntry_isRecomputed() throws InterruptedException {
        IdempotencyStore shortLived = new IdempotencyStore(20);
        AtomicInteger calls = new AtomicInteger();

        shortLived.execute("key-1", "credit:1", calls::incrementAndGet);
        Thread.sleep(50);
        shortLived.execute("key-1", "credit:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
    }
}
