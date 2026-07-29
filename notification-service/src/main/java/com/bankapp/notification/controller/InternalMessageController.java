package com.bankapp.notification.controller;

import com.bankapp.notification.dto.CreateMessageRequest;
import com.bankapp.notification.dto.MessageResponse;
import com.bankapp.notification.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

// Service-to-service only, same trust-boundary simplification as identity-service's
// InternalUserController - never routed through the public gateway (see gateway-service's
// application.yml, which only proxies /api/**), and explicitly permitAll'd in SecurityConfig
// rather than bearer-authenticated. Deliberately separate from the public, self-scoped
// /api/notifications/messages endpoints in MessageController: this one lets a caller create a
// message for *any* ownerId, which must never be reachable by an end user's own JWT.
@RestController
@RequestMapping("/internal/messages")
@RequiredArgsConstructor
public class InternalMessageController {

    private final MessageService messageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MessageResponse> create(@Valid @RequestBody CreateMessageRequest request) {
        return messageService.create(request);
    }
}
