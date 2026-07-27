package com.bankapp.security;

/**
 * Generates and delivers a one-time login code for the given email. The returned code is what
 * the server stores and later checks the user's input against - implementations are free to
 * choose how (or whether) the code actually reaches the user.
 */
public interface OtpClient {
    String issueCode(String email);
}
