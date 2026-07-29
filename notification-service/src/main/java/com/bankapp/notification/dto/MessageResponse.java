package com.bankapp.notification.dto;

import com.bankapp.notification.model.MessageDocument;
import com.bankapp.notification.model.MessagePriority;

import java.time.Instant;

public record MessageResponse(
        String id,
        String subject,
        String body,
        Instant receivedAt,
        boolean read,
        MessagePriority priority
) {
    public static MessageResponse from(MessageDocument document) {
        return new MessageResponse(
                document.getId(),
                document.getSubject(),
                document.getBody(),
                document.getReceivedAt(),
                document.isRead(),
                document.getPriority()
        );
    }
}
