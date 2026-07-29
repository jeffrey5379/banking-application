package com.bankapp.notification.service;

import com.bankapp.notification.dto.CreateMessageRequest;
import com.bankapp.notification.dto.MessageResponse;
import com.bankapp.notification.model.MessageDocument;
import com.bankapp.notification.redis.MessageEventPublisher;
import com.bankapp.notification.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final MessageEventPublisher messageEventPublisher;

    public Flux<MessageResponse> listForUser(UUID ownerId) {
        return messageRepository.findByOwnerIdOrderByReceivedAtDesc(ownerId)
                .map(MessageResponse::from);
    }

    // Persists first, publishes second: a live-update push must never race ahead of (or exist
    // without) the write it announces. The publish itself is best-effort (see
    // MessageEventPublisher) - if it fails, the message still exists and simply won't appear as
    // a toast until the owner's next GET /api/notifications/messages.
    public Mono<MessageResponse> create(CreateMessageRequest request) {
        MessageDocument document = new MessageDocument(
                null,
                request.ownerId(),
                request.subject(),
                request.body(),
                request.priorityOrDefault(),
                false,
                Instant.now()
        );

        return messageRepository.save(document)
                .flatMap(saved -> messageEventPublisher.publish(saved).thenReturn(saved))
                .map(MessageResponse::from);
    }

    // Scoped to ownerId (not just findById) so one user can never read or mark-as-read another
    // user's message by guessing/enumerating its id.
    public Mono<MessageResponse> markAsRead(UUID ownerId, String id) {
        return messageRepository.findById(id)
                .filter(message -> message.getOwnerId().equals(ownerId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found")))
                .flatMap(message -> {
                    if (message.isRead()) {
                        return Mono.just(message);
                    }
                    message.setRead(true);
                    return messageRepository.save(message);
                })
                .map(MessageResponse::from);
    }
}
