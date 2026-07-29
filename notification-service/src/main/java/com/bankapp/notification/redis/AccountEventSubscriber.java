package com.bankapp.notification.redis;

import com.bankapp.notification.dto.AccountEvent;
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

/**
 * Subscribes exactly once to the "account-events" Redis channel core-banking publishes to, and
 * fans that single subscription out to every connected SSE client via a Sinks.Many. Without this,
 * each browser tab's SSE connection would open its own Redis subscription - fine at demo scale,
 * wasteful (and eventually a connection-limit problem) at real scale.
 *
 * <p>Uses {@code directBestEffort()}, not {@code onBackpressureBuffer()}: this is a live, "what's
 * happening right now" feed, not an inbox with delivery guarantees. {@code onBackpressureBuffer()}
 * retains every emitted item until every subscriber that has ever attached has consumed it -
 * for a browser tab whose EventSource just aborts the connection (closing it for a reconnect,
 * a tab close, a network drop) rather than cleanly cancelling, that subscriber can look "still
 * pending" indefinitely, so the buffer never drains and every *new* subscription - including a
 * brand new login, or the periodic reconnect notification.service.ts does every few minutes -
 * replays the account's entire event history back to whoever just connected (verified locally:
 * a user's own long-past seed-funding events re-arrive as fresh toasts on every reconnect).
 * {@code directBestEffort()} only ever delivers to subscribers that are actually attached and
 * ready *right now*; a client that connects a moment after an event was published simply gets
 * the next one, exactly the semantics this feed needs.
 */
@Component
@Slf4j
public class AccountEventSubscriber {

    private static final String CHANNEL = "account-events";

    private final ReactiveRedisMessageListenerContainer container;
    private final ObjectMapper objectMapper;
    private final Sinks.Many<AccountEvent> sink = Sinks.many().multicast().directBestEffort();

    public AccountEventSubscriber(ReactiveRedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        this.container = new ReactiveRedisMessageListenerContainer(connectionFactory);
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void subscribe() {
        container.receive(ChannelTopic.of(CHANNEL))
                .map(ReactiveSubscription.Message::getMessage)
                .doOnNext(this::handleMessage)
                .doOnError(e -> log.error("account-events subscription error", e))
                .retry()
                .subscribe();
    }

    public Flux<AccountEvent> events() {
        return sink.asFlux();
    }

    // Package-private: the parse/emit step, split out from the Redis wiring above so it's
    // testable without a real (or embedded) Redis - see AccountEventSubscriberTest.
    void handleMessage(String json) {
        AccountEvent event = parse(json);
        if (event != null) {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                log.warn("Failed to emit account event to subscribers: {} ({})", result, json);
            }
        }
    }

    private AccountEvent parse(String json) {
        try {
            return objectMapper.readValue(json, AccountEvent.class);
        } catch (Exception e) {
            log.warn("Discarding malformed account event: {}", json, e);
            return null;
        }
    }
}
