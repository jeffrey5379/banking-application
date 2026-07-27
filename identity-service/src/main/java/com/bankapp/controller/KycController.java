package com.bankapp.controller;

import com.bankapp.dto.KycDtos.KycStatusResponse;
import com.bankapp.dto.KycDtos.SubmitIdentityRequest;
import com.bankapp.dto.KycDtos.UploadUrlRequest;
import com.bankapp.dto.KycDtos.UploadUrlResponse;
import com.bankapp.service.KycService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
public class KycController {

    private final KycService kycService;

    @GetMapping("/status")
    public ResponseEntity<KycStatusResponse> getStatus(Authentication auth) {
        return ResponseEntity.ok(kycService.getStatus(auth.getName()));
    }

    @PostMapping("/identity")
    public ResponseEntity<KycStatusResponse> submitIdentity(Authentication auth,
                                                             @Valid @RequestBody SubmitIdentityRequest req) {
        return ResponseEntity.ok(kycService.submitIdentity(auth.getName(), req));
    }

    @PostMapping("/documents/upload-url")
    public ResponseEntity<UploadUrlResponse> requestUploadUrl(Authentication auth,
                                                                @Valid @RequestBody UploadUrlRequest req) {
        return ResponseEntity.ok(kycService.requestUploadUrl(auth.getName(), req));
    }

    @PostMapping("/documents/{documentId}/complete")
    public ResponseEntity<KycStatusResponse> completeUpload(Authentication auth, @PathVariable UUID documentId) {
        return ResponseEntity.ok(kycService.completeUpload(auth.getName(), documentId));
    }
}
