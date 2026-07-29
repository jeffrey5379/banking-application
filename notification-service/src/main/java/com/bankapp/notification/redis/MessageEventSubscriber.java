package com.bankapp.notification.redis;

import com.bankapp.notification.dto.MessageEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

// Mirrors AccountEventSubscriber exactly, but for the "message-events" channel MessageEventPublisher
// publishes to (see that class for the single-subscription-fanned-out-via-Sinks reasoning and why
// directBestEffort() rather than onBackpressureBuffer()).
@Component
@Slf4j
public class MessageEventSubscriber {

    private static final String CHANNEL = "message-events";

    private final ReactiveRedisMessageListenerContainer container;
    private final ObjectMapper objectMapper;
    private final Sinks.Many<MessageEvent> sink = Sinks.many().multicast().directBestEffort();

    public MessageEventSubscriber(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.container = new ReactiveRedisMessageListenerContainer(connectionFactory);
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void subscribe() {
        container.receive(ChannelTopic.of(CHANNEL))
                .map(ReactiveSubscription.Message::getMessage)
                .doOnNext(this::handleMessage)
                .doOnError(e -> log.error("message-events subscription error", e))
                .retry()
                .subscribe();
    }

    public Flux<MessageEvent> events() {
        return sink.asFlux();
    }

    // Package-private: split out from the Redis wiring above so it's testable without a real (or
    // embedded) Redis - see AccountEventSubscriberTest for the equivalent pattern.
    void handleMessage(String json) {
        MessageEvent event = parse(json);
        if (event != null) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                log.warn("Failed to emit message event to subscribers: {} ({})", result, json);
            }
        }
    }

    private MessageEvent parse(String json) {
        try {
            return objectMapper.readValue(json, MessageEvent.class);
        } catch (Exception e) {
            log.warn("Discarding malformed message event: {}", json, e);
            return null;
        }
    }
}
