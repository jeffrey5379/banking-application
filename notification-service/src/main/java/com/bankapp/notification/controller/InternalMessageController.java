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
