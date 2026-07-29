package com.bankapp.notification.dto;

import com.bankapp.notification.model.MessagePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// Body for POST /internal/messages. priority is optional and defaults to NORMAL - most
// system-generated messages (card issued, maintenance notice, ...) aren't urgent, so callers
// shouldn't have to spell that out every time.
public record CreateMessageRequest(
        @NotNull UUID ownerId,
        @NotBlank String subject,
        @NotBlank String body,
        MessagePriority priority
) {
    public MessagePriority priorityOrDefault() {
        return priority != null ? priority : MessagePriority.NORMAL;
    }
}
