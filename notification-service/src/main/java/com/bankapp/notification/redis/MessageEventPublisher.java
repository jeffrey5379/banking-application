package com.bankapp.notification.redis;

import com.bankapp.notification.dto.MessageEvent;
import com.bankapp.notification.model.MessageDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// Publishes a "message created" event for MessageEventSubscriber (running in every
// notification-service instance) to relay to the owner's browser over SSE. Goes through Redis
// Pub/Sub rather than a direct in-process handoff so this still works if notification-service
// ever runs as more than one instance behind the ALB - the instance handling the create request
// isn't necessarily the one holding that user's open SSE connection (same reasoning as
// TicketService/AccountEventSubscriber).
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageEventPublisher {

    public static final String CHANNEL = "message-events";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // Best-effort: a live-update push failing must never fail the message creation it describes,
    // which has already been persisted by the time this runs.
    public Mono<Void> publish(MessageDocument document) {
        MessageEvent event = new MessageEvent(
                document.getOwnerId(),
                document.getId(),
                document.getSubject(),
                document.getBody(),
                document.getReceivedAt(),
                document.getPriority()
        );

        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("Failed to serialize message event for message {}", document.getId(), e);
            return Mono.empty();
        }

        return redisTemplate.convertAndSend(CHANNEL, json)
                .onErrorResume(e -> {
                    log.warn("Failed to publish message event for message {}", document.getId(), e);
                    return Mono.empty();
                })
                .then();
    }
}
