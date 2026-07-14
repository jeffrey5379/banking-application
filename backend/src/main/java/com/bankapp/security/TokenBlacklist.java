package com.bankapp.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class TokenBlacklist {

    private final ConcurrentHashMap<String, Long> revoked = new ConcurrentHashMap<>();

    public void revoke(String token, long expiryMs) {
        revoked.put(token, expiryMs);
    }

    public boolean isRevoked(String token) {
        Long expiry = revoked.get(token);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            revoked.remove(token);
            return false;
        }
        return true;
    }
}
