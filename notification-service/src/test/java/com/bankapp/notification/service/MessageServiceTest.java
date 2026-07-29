package com.bankapp.notification.service;

import com.bankapp.notification.dto.CreateMessageRequest;
import com.bankapp.notification.model.MessageDocument;
import com.bankapp.notification.model.MessagePriority;
import com.bankapp.notification.redis.MessageEventPublisher;
import com.bankapp.notification.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageServiceTest {

    @Mock private MessageRepository messageRepository;
    @Mock private MessageEventPublisher messageEventPublisher;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(messageRepository, messageEventPublisher);
    }

    @Test
    void listForUser_returnsOnlyThatUsersMessages() {
        UUID ownerId = UUID.randomUUID();
        MessageDocument doc = new MessageDocument("msg-1", ownerId, "Subject", "Body",
                MessagePriority.NORMAL, false, Instant.now());
        when(messageRepository.findByOwnerIdOrderByReceivedAtDesc(ownerId)).thenReturn(Flux.just(doc));

        StepVerifier.create(messageService.listForUser(ownerId))
                .assertNext(response -> assertThat(response.id()).isEqualTo("msg-1"))
                .verifyComplete();
    }

    @Test
    void markAsRead_ownMessage_marksItReadAndSaves() {
        UUID ownerId = UUID.randomUUID();
        MessageDocument doc = new MessageDocument("msg-1", ownerId, "Subject", "Body",
                MessagePriority.NORMAL, false, Instant.now());
        when(messageRepository.findById("msg-1")).thenReturn(Mono.just(doc));
        when(messageRepository.save(any(MessageDocument.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(messageService.markAsRead(ownerId, "msg-1"))
                .assertNext(response -> assertThat(response.read()).isTrue())
                .verifyComplete();
    }

    @Test
    void markAsRead_someoneElsesMessage_returns404WithoutSaving() {
        UUID ownerId = UUID.randomUUID();
        MessageDocument doc = new MessageDocument("msg-1", UUID.randomUUID(), "Subject", "Body",
                MessagePriority.NORMAL, false, Instant.now());
        when(messageRepository.findById("msg-1")).thenReturn(Mono.just(doc));

        StepVerifier.create(messageService.markAsRead(ownerId, "msg-1"))
                .expectErrorMatches(e -> e instanceof ResponseStatusException rse
                        && rse.getStatusCode().value() == 404)
                .verify();

        verify(messageRepository, never()).save(any());
    }

    @Test
    void create_savesThenPublishesInOrder_andDefaultsPriorityToNormal() {
        UUID ownerId = UUID.randomUUID();
        CreateMessageRequest request = new CreateMessageRequest(ownerId, "Subject", "Body", null);

        ArgumentCaptor<MessageDocument> savedCaptor = ArgumentCaptor.forClass(MessageDocument.class);
        when(messageRepository.save(savedCaptor.capture()))
                .thenAnswer(inv -> {
                    MessageDocument doc = inv.getArgument(0);
                    doc.setId("msg-1");
                    return Mono.just(doc);
                });
        when(messageEventPublisher.publish(any(MessageDocument.class))).thenReturn(Mono.empty());

        StepVerifier.create(messageService.create(request))
                .assertNext(response -> {
                    assertThat(response.id()).isEqualTo("msg-1");
                    assertThat(response.priority()).isEqualTo(MessagePriority.NORMAL);
                    assertThat(response.read()).isFalse();
                })
                .verifyComplete();

        assertThat(savedCaptor.getValue().getOwnerId()).isEqualTo(ownerId);
        assertThat(savedCaptor.getValue().isRead()).isFalse();
        verify(messageEventPublisher).publish(any(MessageDocument.class));
    }

    @Test
    void create_neverPublishesIfSaveFails() {
        CreateMessageRequest request = new CreateMessageRequest(UUID.randomUUID(), "Subject", "Body", MessagePriority.HIGH);
        when(messageRepository.save(any(MessageDocument.class)))
                .thenReturn(Mono.error(new RuntimeException("mongo down")));

        StepVerifier.create(messageService.create(request)).expectError(RuntimeException.class).verify();

        verify(messageEventPublisher, never()).publish(any());
    }
}
