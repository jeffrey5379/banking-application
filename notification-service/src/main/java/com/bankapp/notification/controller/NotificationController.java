package com.bankapp.notification.controller;

import com.bankapp.notification.redis.AccountEventSubscriber;
import com.bankapp.notification.redis.MessageEventSubscriber;
import com.bankapp.notification.service.TicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final TicketService ticketService;
    private final AccountEventSubscriber accountEventSubscriber;
    private final MessageEventSubscriber messageEventSubscriber;

    // Bearer-authenticated (see SecurityConfig) - the frontend calls this right before opening
    // the SSE connection, using the exact same request pipeline (and Authorization header) as
    // every other API call.
    @PostMapping("/ticket")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Map<String, String>> issueTicket(@AuthenticationPrincipal UUID userId) {
        return ticketService.issue(userId).map(ticket -> Map.of("ticket", ticket));
    }

    // Typed Object, not AccountEvent: this one stream now carries two different event shapes
    // (balance-update, message-created), distinguished for the client by the SSE "event" field,
    // not by the (irrelevant at the wire level) Java type of whichever record produced the data.
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@RequestParam String ticket) {
        return ticketService.consume(ticket)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired ticket")))
                .flatMapMany(userId -> Flux.merge(
                        accountEventSubscriber.events()
                                .filter(event -> event.ownerId().equals(userId))
                                .map(event -> ServerSentEvent.builder((Object) event).event("balance-update").build()),
                        messageEventSubscriber.events()
                                .filter(event -> event.ownerId().equals(userId))
                                .map(event -> ServerSentEvent.builder((Object) event).event("message-created").build())
                    ).mergeWith(heartbeat()));
    }

    private Flux<ServerSentEvent<Object>> heartbeat() {
        return Flux.interval(Duration.ofSeconds(20))
                .map(tick -> ServerSentEvent.builder().comment("keep-alive").build());
    }
}
