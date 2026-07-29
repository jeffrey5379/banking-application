package com.bankapp.notification.controller;

import com.bankapp.notification.dto.AccountEvent;
import com.bankapp.notification.dto.MessageEvent;
import com.bankapp.notification.model.MessagePriority;
import com.bankapp.notification.redis.AccountEventSubscriber;
import com.bankapp.notification.redis.MessageEventSubscriber;
import com.bankapp.notification.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationControllerTest {

    @Mock private TicketService ticketService;
    @Mock private AccountEventSubscriber accountEventSubscriber;
    @Mock private MessageEventSubscriber messageEventSubscriber;

    private NotificationController controller;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        controller = new NotificationController(ticketService, accountEventSubscriber, messageEventSubscriber);
        webTestClient = WebTestClient.bindToController(controller).build();
        // Every stream_* test below hits the controller's merged Flux, which subscribes to both
        // sources regardless of which one that particular test cares about.
        when(accountEventSubscriber.events()).thenReturn(Flux.empty());
        when(messageEventSubscriber.events()).thenReturn(Flux.empty());
    }

    // /ticket needs an authenticated principal, which the reactive security filter chain
    // (SecurityConfig) supplies over real HTTP - a standalone WebTestClient binding skips that
    // filter chain entirely, so this exercises the handler method directly instead.
    @Test
    void issueTicket_returnsTicketFromTicketService() {
        UUID userId = UUID.randomUUID();
        when(ticketService.issue(userId)).thenReturn(Mono.just("abc123"));

        StepVerifier.create(controller.issueTicket(userId))
                .assertNext(body -> assertThat(body).containsEntry("ticket", "abc123"))
                .verifyComplete();
    }

    @Test
    void stream_invalidTicket_returns401() {
        when(ticketService.consume("bad-ticket")).thenReturn(Mono.empty());

        webTestClient.get().uri("/api/notifications/stream?ticket=bad-ticket")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void stream_validTicket_streamsOnlyThatUsersAccountEvents() {
        UUID userId = UUID.randomUUID();
        AccountEvent mine = new AccountEvent(userId, UUID.randomUUID(), "ACC-AAAAAAAA",
                "TRANSFER_OUT", new BigDecimal("100.00"), new BigDecimal("900.00"), "EUR",
                "Transfer to bob (ACC-BBBBBBBB)", LocalDateTime.now());
        AccountEvent someoneElses = new AccountEvent(UUID.randomUUID(), UUID.randomUUID(), "ACC-BBBBBBBB",
                "TRANSFER_IN", new BigDecimal("100.00"), new BigDecimal("100.00"), "EUR",
                "Transfer from alice (ACC-AAAAAAAA)", LocalDateTime.now());

        when(ticketService.consume("good-ticket")).thenReturn(Mono.just(userId));
        // Merged with an infinite keep-alive heartbeat in the controller, so the response body
        // never completes on its own (correct for a real SSE stream) - take(1)/thenCancel below
        // is why, rather than waiting on expectBodyList()/verifyComplete().
        when(accountEventSubscriber.events()).thenReturn(Flux.just(someoneElses, mine));

        var result = webTestClient.get().uri("/api/notifications/stream?ticket=good-ticket")
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(AccountEvent.class);

        StepVerifier.create(result.getResponseBody().take(1))
                .expectNext(mine)
                .verifyComplete();
    }

    @Test
    void stream_validTicket_streamsOnlyThatUsersMessageEvents() {
        UUID userId = UUID.randomUUID();
        MessageEvent mine = new MessageEvent(userId, "msg-1", "Suspicious login attempt detected",
                "We detected a login attempt from a new device.", Instant.now(), MessagePriority.HIGH);
        MessageEvent someoneElses = new MessageEvent(UUID.randomUUID(), "msg-2", "Your card has been issued",
                "Your new card is on its way.", Instant.now(), MessagePriority.NORMAL);

        when(ticketService.consume("good-ticket")).thenReturn(Mono.just(userId));
        when(messageEventSubscriber.events()).thenReturn(Flux.just(someoneElses, mine));

        var result = webTestClient.get().uri("/api/notifications/stream?ticket=good-ticket")
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(MessageEvent.class);

        StepVerifier.create(result.getResponseBody().take(1))
                .expectNext(mine)
                .verifyComplete();
    }
}
