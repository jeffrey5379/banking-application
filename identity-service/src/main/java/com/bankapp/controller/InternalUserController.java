package com.bankapp.controller;

import com.bankapp.dto.AuthDtos.UserResponse;
import com.bankapp.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Service-to-service only (called by core-banking's DataSeeder to resolve already-registered
// demo users' public ids without re-registering them), never routed through the public gateway.
// Same trust-boundary simplification as InternalKycController.
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{username}")
    public ResponseEntity<UserResponse> getByUsername(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserByUsername(username));
    }
}
