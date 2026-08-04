package com.bankapp.controller;

import com.bankapp.dto.AdminDtos.AdminUserSummary;
import com.bankapp.dto.AdminDtos.SetActiveRequest;
import com.bankapp.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Every endpoint here is gated by the caller's live role (re-derived from the DB on every
// request via UserService.loadUserByUsername -> JwtAuthFilter), not a JWT claim - so revoking
// admin rights takes effect immediately, without waiting for token expiry.
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public List<AdminUserSummary> listUsers() {
        return adminService.listUsers();
    }

    @PostMapping("/users/{id}/active")
    public AdminUserSummary setActive(@PathVariable UUID id, @Valid @RequestBody SetActiveRequest req) {
        return adminService.setActive(id, req.active());
    }
}
