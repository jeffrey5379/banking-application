package com.bankapp.controller;

import com.bankapp.dto.KycDtos.KycEligibilityResponse;
import com.bankapp.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Service-to-service only (called by core-banking's KycStatusClient)
@RestController
@RequestMapping("/internal/kyc")
@RequiredArgsConstructor
public class InternalKycController {

    private final KycService kycService;

    @GetMapping("/{userId}/eligibility")
    public ResponseEntity<KycEligibilityResponse> getEligibility(@PathVariable UUID userId) {
        return ResponseEntity.ok(kycService.getEligibility(userId));
    }
}
