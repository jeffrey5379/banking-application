package com.bankapp.service;

import com.bankapp.exception.IdempotencyInProgressException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// RedisTemplate is mocked, but backed by a real in-memory Map/Set so multi-key scenarios (same
// key returns cached, different keys are independent, lock contention) behave like real Redis
// would - pure interaction verification (no backing state) can't express "the second call sees
// what the first call wrote" without a lot of brittle per-key stubbing.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IdempotencyStoreTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    private final Map<String, Object> fakeRedis = new ConcurrentHashMap<>();
    private final Set<String> locks = ConcurrentHashMap.newKeySet();

    private IdempotencyStore store;

    @BeforeEach
    void setUp() {
        fakeRedis.clear();
        locks.clear();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(anyString())).thenAnswer(inv -> fakeRedis.get((String) inv.getArgument(0)));

        doAnswer(inv -> {
            fakeRedis.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), any(), any(Duration.class));

        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return locks.add(key);
        });

        when(redisTemplate.delete(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            locks.remove(key);
            return fakeRedis.remove(key) != null;
        });

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null));

        store = new IdempotencyStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nullKey_alwaysInvokesAction() {
        AtomicInteger calls = new AtomicInteger();
        store.execute(null, "transfer:1", calls::incrementAndGet);
        store.execute(null, "transfer:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void blankKey_alwaysInvokesAction() {
        AtomicInteger calls = new AtomicInteger();
        store.execute("", "transfer:1", calls::incrementAndGet);
        store.execute("   ", "transfer:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void repeatedKey_invokesActionOnceAndReplaysResult() {
        AtomicInteger calls = new AtomicInteger();
        Integer first = store.execute("key-1", "transfer:1", calls::incrementAndGet);
        Integer second = store.execute("key-1", "transfer:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(1);
        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
    }

    @Test
    void differentKeys_bothInvokeAction() {
        AtomicInteger calls = new AtomicInteger();
        store.execute("key-1", "transfer:1", calls::incrementAndGet);
        store.execute("key-2", "transfer:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void sameKeyDifferentOperation_bothInvokeAction() {
        AtomicInteger calls = new AtomicInteger();
        store.execute("key-1", "transfer:1", calls::incrementAndGet);
        store.execute("key-1", "exchange:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void sameKeyDifferentUser_bothInvokeAction() {
        AtomicInteger calls = new AtomicInteger();
        store.execute("key-1", "transfer:1", calls::incrementAndGet);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("bob", null));
        store.execute("key-1", "transfer:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void failedAttempt_isNotCached_andCanBeRetried() {
        AtomicInteger calls = new AtomicInteger();
        try {
            store.execute("key-1", "transfer:1", () -> {
                calls.incrementAndGet();
                throw new IllegalStateException("insufficient funds");
            });
        } catch (IllegalStateException ignored) {
            // expected
        }

        Integer result = store.execute("key-1", "transfer:1", calls::incrementAndGet);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(result).isEqualTo(2);
    }

    @Test
    void failedAttempt_releasesLockImmediately() {
        try {
            store.execute("key-1", "transfer:1", () -> {
                throw new IllegalStateException("insufficient funds");
            });
        } catch (IllegalStateException ignored) {
            // expected
        }

        // A second attempt right after must be able to acquire the lock fresh, not wait/fail.
        Integer result = store.execute("key-1", "transfer:1", () -> 42);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void lockHeldByAnotherInstance_waitsAndReplaysResultOnceWritten() throws Exception {
        String lockKey = "idempotency:alice|transfer:1|key-1:lock";
        String resultKey = "idempotency:alice|transfer:1|key-1:result";
        locks.add(lockKey); // simulate another instance already holding the lock

        AtomicInteger calls = new AtomicInteger();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // SecurityContextHolder defaults to a ThreadLocal strategy - the "alice" auth set in
            // setUp() on the test thread isn't visible from the executor's thread, so it has to
            // be set again here, on the thread that actually calls store.execute().
            Future<Integer> future = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("alice", null));
                return store.execute("key-1", "transfer:1", calls::incrementAndGet);
            });

            Thread.sleep(250); // let the poll loop run a couple of iterations first
            fakeRedis.put(resultKey, 99); // "other instance" finishes and writes the result

            Integer result = future.get(5, TimeUnit.SECONDS);

            assertThat(result).isEqualTo(99);
            assertThat(calls.get()).isZero(); // our own action must never have run
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void lockNeverReleased_throwsIdempotencyInProgressAfterMaxWait() {
        IdempotencyStore shortWaitStore = new IdempotencyStore(
                redisTemplate, Duration.ofHours(24), Duration.ofSeconds(30),
                Duration.ofMillis(20), Duration.ofMillis(100));

        String lockKey = "idempotency:alice|transfer:1|key-1:lock";
        locks.add(lockKey); // held forever, no result ever appears

        assertThatThrownBy(() -> shortWaitStore.execute("key-1", "transfer:1", () -> 1))
                .isInstanceOf(IdempotencyInProgressException.class);
    }
}
