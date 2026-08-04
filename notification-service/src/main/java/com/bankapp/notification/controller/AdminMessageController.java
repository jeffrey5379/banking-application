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

// Routed through the gateway (see gateway-service's application.yml) unlike InternalMessageController
// - reachable with a real user's bearer token, but gated to ROLE_ADMIN only
@RestController
@RequestMapping("/api/admin/messages")
@RequiredArgsConstructor
public class AdminMessageController {

    private final MessageService messageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MessageResponse> create(@Valid @RequestBody CreateMessageRequest request) {
        return messageService.create(request);
    }
}
