package com.bankapp.notification.dto;

import com.bankapp.notification.model.MessagePriority;

import java.time.Instant;
import java.util.UUID;

// Wire shape for the "message-events" Redis channel and the "message-created" SSE event - a
// freshly created message is always unread, so unlike MessageResponse there's no read field to
// carry.
public record MessageEvent(
        UUID ownerId,
        String id,
        String subject,
        String body,
        Instant receivedAt,
        MessagePriority priority
) {}
