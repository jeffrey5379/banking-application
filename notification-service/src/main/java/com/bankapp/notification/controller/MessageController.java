package com.bankapp.notification.controller;

import com.bankapp.notification.dto.MessageResponse;
import com.bankapp.notification.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

// Bearer-authenticated like every other path except /api/notifications/stream (see
// SecurityConfig) - userId comes off the validated JWT, never a request parameter, so a caller
// can only ever list/mark-as-read their own messages.
@RestController
@RequestMapping("/api/notifications/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public Flux<MessageResponse> list(@AuthenticationPrincipal UUID userId) {
        return messageService.listForUser(userId);
    }

    @PostMapping("/{id}/read")
    public Mono<MessageResponse> markAsRead(@AuthenticationPrincipal UUID userId, @PathVariable String id) {
        return messageService.markAsRead(userId, id);
    }
}
