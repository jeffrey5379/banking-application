package com.bankapp.notification.redis;

import com.bankapp.notification.model.MessageDocument;
import com.bankapp.notification.model.MessagePriority;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageEventPublisherTest {

    @Mock private ReactiveStringRedisTemplate redisTemplate;

    private MessageEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        publisher = new MessageEventPublisher(redisTemplate, objectMapper);
    }

    @Test
    void publish_sendsSerializedEventToMessageEventsChannel() {
        UUID ownerId = UUID.randomUUID();
        MessageDocument document = new MessageDocument("msg-1", ownerId, "Subject", "Body",
                MessagePriority.HIGH, false, Instant.parse("2026-07-28T10:15:30Z"));

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        when(redisTemplate.convertAndSend(channelCaptor.capture(), payloadCaptor.capture()))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(publisher.publish(document)).verifyComplete();

        assertThat(channelCaptor.getValue()).isEqualTo("message-events");
        assertThat(payloadCaptor.getValue())
                .contains("\"ownerId\":\"" + ownerId + "\"")
                .contains("\"id\":\"msg-1\"")
                .contains("\"subject\":\"Subject\"")
                .contains("\"priority\":\"HIGH\"");
    }

    @Test
    void publish_neverErrorsEvenIfTheRedisPublishFails() {
        MessageDocument document = new MessageDocument("msg-1", UUID.randomUUID(), "Subject", "Body",
                MessagePriority.NORMAL, false, Instant.now());

        when(redisTemplate.convertAndSend(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("redis down")));

        // Best-effort: publish() must complete successfully even when the underlying send fails,
        // since a live-update push failing must never fail the message creation it describes.
        StepVerifier.create(publisher.publish(document)).verifyComplete();
    }
}
