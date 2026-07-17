package com.bankapp.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockOtpClientTest {

    private final MockOtpClient client = new MockOtpClient();

    @Test
    void issueCode_alwaysReturnsTheFixedMockCode() {
        assertThat(client.issueCode("alice@example.com")).isEqualTo("111111");
        assertThat(client.issueCode("bob@example.com")).isEqualTo("111111");
    }
}
