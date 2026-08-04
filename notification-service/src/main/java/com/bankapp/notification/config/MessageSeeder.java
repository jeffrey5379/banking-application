package com.bankapp.notification.config;

import com.bankapp.notification.model.MessageDocument;
import com.bankapp.notification.model.MessagePriority;
import com.bankapp.notification.repository.MessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class MessageSeeder implements CommandLineRunner {

    private static final List<String> DEMO_USERNAMES = List.of("alice", "bob", "carol");

    private final MessageRepository messageRepository;
    private final WebClient identityClient;

    public MessageSeeder(MessageRepository messageRepository,
                          WebClient.Builder webClientBuilder,
                          @Value("${identity.service.url}") String identityServiceUrl) {
        this.messageRepository = messageRepository;
        this.identityClient = webClientBuilder.baseUrl(identityServiceUrl).build();
    }

    @Override
    public void run(String... args) {
        boolean alreadySeeded = Boolean.TRUE.equals(
                messageRepository.count().map(count -> count > 0).block());
        if (alreadySeeded) {
            log.info("Messages already seeded, skipping.");
            return;
        }

        log.info("Seeding demo messages...");
        DEMO_USERNAMES.forEach(this::seedFor);
        log.info("Message seeding complete.");
    }

    private void seedFor(String username) {
        UUID userId = resolveUserId(username);
        Instant now = Instant.now();

        messageRepository.saveAll(List.of(
                new MessageDocument(null, userId, "Suspicious login attempt detected",
                        "We detected a login attempt to your account from a new device. If this wasn't you, please contact support immediately and change your password.",
                        MessagePriority.HIGH, false, now.minus(2, ChronoUnit.DAYS)),
                new MessageDocument(null, userId, "Your card has been issued",
                        "Your new Octopus Bank debit card has been issued and is on its way to your registered address. It should arrive within 5-7 business days.",
                        MessagePriority.NORMAL, false, now.minus(5, ChronoUnit.DAYS)),
                new MessageDocument(null, userId, "Scheduled maintenance",
                        "Octopus Bank online services will be unavailable this Saturday between 01:00 and 03:00 CET due to scheduled maintenance.",
                        MessagePriority.NORMAL, true, now.minus(11, ChronoUnit.DAYS))
        )).blockLast();
    }

    private UUID resolveUserId(String username) {
        InternalUserResponse user = identityClient.get()
                .uri("/internal/users/{username}", username)
                .retrieve()
                .bodyToMono(InternalUserResponse.class)
                .block();
        if (user == null) {
            throw new IllegalStateException("identity-service has no seeded user named " + username);
        }
        return user.id();
    }

    private record InternalUserResponse(UUID id, String username, String email) {
    }
}
