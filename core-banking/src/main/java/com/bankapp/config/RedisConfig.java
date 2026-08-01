package com.bankapp.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// JSON (not JDK) serialization for cached values, since IdempotencyStore caches arbitrary
// response records (AccountSummaryResponse, List<OperationResponse>, ...) whose exact type
// varies by call site - GenericJackson2JsonRedisSerializer embeds type info so the value comes
// back as the correct type on read, not just a generic Map.
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(valueSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(valueSerializer());
        template.afterPropertiesSet();
        return template;
    }

    // The no-arg constructor's internal ObjectMapper has no java.time support registered, so any
    // cached DTO with a LocalDateTime field (e.g. OperationResponse.createdAt) throws
    // SerializationException at write time. configure() is the sanctioned extension point for
    // tweaking that internal mapper without losing the @class type-info embedding the no-arg
    // constructor sets up - a plain `new ObjectMapper().registerModule(...)` passed to the
    // ObjectMapper-accepting constructor would skip that setup and break type-safe deserialization.
    GenericJackson2JsonRedisSerializer valueSerializer() {
        return new GenericJackson2JsonRedisSerializer()
                .configure(mapper -> mapper.registerModule(new JavaTimeModule()));
    }
}
