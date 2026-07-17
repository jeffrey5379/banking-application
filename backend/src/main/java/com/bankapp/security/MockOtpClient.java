package com.bankapp.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev/test stand-in for {@link OtpClient}: no email is ever sent, the code is always the same
 * fixed value so local development and automated tests never need to read an inbox or log file.
 */
@Slf4j
@Component
@Profile("!prod")
public class MockOtpClient implements OtpClient {

    static final String MOCK_CODE = "111111";

    @Override
    public String issueCode(String email) {
        log.info("[DEV] OTP code for {}: {}", email, MOCK_CODE);
        return MOCK_CODE;
    }
}
