package com.bankapp.notification.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document(collection = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDocument {

    @Id
    private String id;

    @Indexed
    private UUID ownerId;

    private String subject;
    private String body;
    private MessagePriority priority;
    private boolean read;
    private Instant receivedAt;
}
