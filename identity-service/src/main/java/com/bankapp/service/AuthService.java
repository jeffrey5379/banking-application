package com.bankapp.service;

import com.bankapp.dto.AuthDtos.*;
import com.bankapp.security.JwtService;
import com.bankapp.security.OtpClient;
import com.bankapp.security.OtpStore;
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
    private final OtpClient otpClient;
    private final OtpStore otpStore;

    // Same two-step shape as login: creating the account never issues a JWT by itself. A code is
    // emailed (or in dev, mocked) and verifyOtp() is what actually establishes the session -
    // otherwise a registration with a typo'd/unowned email would still get the requester in.
    public LoginChallengeResponse register(RegisterRequest req) {
        String encodedPassword = passwordEncoder.encode(req.password());
        UserResponse created = userService.createUser(req.username(), req.email(), encodedPassword);
        String code = otpClient.issueCode(created.email());
        String challengeToken = otpStore.createChallenge(created.username(), code);
        return new LoginChallengeResponse(challengeToken);
    }

    public void revokeToken(String token) {
        tokenBlacklist.revoke(token, jwtService.extractExpiration(token).getTime());
    }

    // Step 1: verify password, then issue and email/mock a one-time code. No JWT yet.
    public LoginChallengeResponse login(LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        UserResponse user = userService.getUserByUsername(req.username());
        String code = otpClient.issueCode(user.email());
        String challengeToken = otpStore.createChallenge(req.username(), code);
        return new LoginChallengeResponse(challengeToken);
    }

    // Step 2: verify the code, then issue the JWT.
    public AuthResponse verifyOtp(VerifyOtpRequest req) {
        String username = otpStore.verify(req.challengeToken(), req.code());
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        UserResponse user = userService.getUserByUsername(username);
        String token = jwtService.generateToken(userDetails, user.id());
        return new AuthResponse(token, user.id(), user.username());
    }
}
