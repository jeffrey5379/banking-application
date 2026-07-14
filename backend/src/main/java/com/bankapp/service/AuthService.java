package com.bankapp.service;

import com.bankapp.dto.BankDtos.*;
import com.bankapp.security.JwtService;
import com.bankapp.security.TokenBlacklist;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklist tokenBlacklist;

    public AuthResponse register(RegisterRequest req) {
        String encodedPassword = passwordEncoder.encode(req.password());
        UserResponse created = userService.createUser(req.username(), req.email(), encodedPassword);
        UserDetails userDetails = userDetailsService.loadUserByUsername(req.username());
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, created.id(), created.username());
    }

    public void revokeToken(String token) {
        tokenBlacklist.revoke(token, jwtService.extractExpiration(token).getTime());
    }

    public AuthResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        UserDetails userDetails = userDetailsService.loadUserByUsername(req.username());
        UserResponse user = userService.getUserByUsername(req.username());
        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, user.id(), user.username());
    }
}
