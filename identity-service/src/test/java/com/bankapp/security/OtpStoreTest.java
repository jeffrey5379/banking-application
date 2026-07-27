package com.bankapp.security;

import com.bankapp.exception.InvalidOtpException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Redis is mocked here - single-use/expiry guarantees ultimately come from real Redis behavior
// (DEL, TTL), which is covered by a live verification rather than a unit test. These tests cover
// our own logic: what gets stored, what triggers delete vs. leaves the challenge alive, and the
// attempt-cap boundary.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpStoreTest {

    private static final String KEY = "otp:challenge:tok-1";

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    private OtpStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        store = new OtpStore(redisTemplate);
    }

    @Test
    void createChallenge_storesFieldsAndSetsFiveMinuteTtl() {
        store.createChallenge("alice", "111111");

        verify(hashOperations).putAll(anyString(),
                eq(Map.of("username", "alice", "code", "111111", "attempts", "0")));
        verify(redisTemplate).expire(anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void verify_correctCode_returnsUsernameAndDeletesChallenge() {
        when(hashOperations.get(KEY, "code")).thenReturn("111111");
        when(hashOperations.increment(KEY, "attempts", 1)).thenReturn(1L);
        when(hashOperations.get(KEY, "username")).thenReturn("alice");

        String username = store.verify("tok-1", "111111");

        assertThat(username).isEqualTo("alice");
        verify(redisTemplate).delete(KEY);
    }

    @Test
    void verify_missingChallenge_throwsWithoutDeleting() {
        when(hashOperations.get(KEY, "code")).thenReturn(null);

        assertThatThrownBy(() -> store.verify("tok-1", "111111"))
                .isInstanceOf(InvalidOtpException.class);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void verify_wrongCodeUnderAttemptCap_throwsButKeepsChallengeAlive() {
        when(hashOperations.get(KEY, "code")).thenReturn("111111");
        when(hashOperations.increment(KEY, "attempts", 1)).thenReturn(1L);

        assertThatThrownBy(() -> store.verify("tok-1", "000000"))
                .isInstanceOf(InvalidOtpException.class);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void verify_attemptsExceedCap_throwsAndDeletesChallenge() {
        when(hashOperations.get(KEY, "code")).thenReturn("111111");
        when(hashOperations.increment(KEY, "attempts", 1)).thenReturn(6L);

        assertThatThrownBy(() -> store.verify("tok-1", "000000"))
                .isInstanceOf(InvalidOtpException.class)
                .hasMessageContaining("Too many");

        verify(redisTemplate).delete(KEY);
    }

    @Test
    void verify_attemptsExactlyAtCap_stillAcceptsCorrectCode() {
        when(hashOperations.get(KEY, "code")).thenReturn("111111");
        when(hashOperations.increment(KEY, "attempts", 1)).thenReturn(5L);
        when(hashOperations.get(KEY, "username")).thenReturn("alice");

        String username = store.verify("tok-1", "111111");

        assertThat(username).isEqualTo("alice");
    }
}
