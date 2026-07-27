package com.bankapp.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Production {@link OtpClient}: generates a random 6-digit code and emails it to the user.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class EmailOtpClient implements OtpClient {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;

    // Without an explicit From, JavaMail falls back to a locally-constructed address (e.g.
    // <user>@<ecs-task-hostname>) that SMTP relays like Brevo reject as an unverified sender.
    @Value("${mail.from}")
    private String fromAddress;

    @Override
    public String issueCode(String email) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("Your Octopus Bank login code");
        message.setText("Your one-time login code is: " + code + "\n\nThis code expires in 5 minutes.");
        mailSender.send(message);

        return code;
    }
}
