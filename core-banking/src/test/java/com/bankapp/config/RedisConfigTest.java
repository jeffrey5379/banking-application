package com.bankapp.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Regression test for a bug found in production logs: the value serializer's ObjectMapper had no
// java.time support registered, so caching any DTO with a LocalDateTime field (e.g.
// OperationResponse.createdAt, cached by IdempotencyStore on every exchange/transfer) threw
// SerializationException instead of completing the request.
class RedisConfigTest {

    private record SampleWithDate(String name, LocalDateTime createdAt) {}

    @Test
    void valueSerializer_roundTripsATypeContainingLocalDateTime() {
        GenericJackson2JsonRedisSerializer serializer = new RedisConfig().valueSerializer();
        SampleWithDate original = new SampleWithDate("op-1", LocalDateTime.of(2026, 7, 26, 10, 30));

        byte[] serialized = serializer.serialize(original);

        assertThat(serialized).isNotNull();
        assertThat(serializer.deserialize(serialized)).isEqualTo(original);
    }
}
