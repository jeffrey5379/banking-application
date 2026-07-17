package com.bankapp.security;

import lombok.RequiredArgsConstructor;
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

    @Override
    public String issueCode(String email) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Your Octopus Bank login code");
        message.setText("Your one-time login code is: " + code + "\n\nThis code expires in 5 minutes.");
        mailSender.send(message);

        return code;
    }
}
