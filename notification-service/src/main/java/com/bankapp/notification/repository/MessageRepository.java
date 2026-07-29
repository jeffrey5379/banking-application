package com.bankapp.notification.repository;

import com.bankapp.notification.model.MessageDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

public interface MessageRepository extends ReactiveMongoRepository<MessageDocument, String> {
    Flux<MessageDocument> findByOwnerIdOrderByReceivedAtDesc(UUID ownerId);
}
