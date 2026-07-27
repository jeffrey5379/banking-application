package com.bankapp.dto;

import com.bankapp.model.kyc.DocumentType;
import com.bankapp.model.kyc.IssuingCountry;
import com.bankapp.model.kyc.KycLevel;
import com.bankapp.model.kyc.KycStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class KycDtos {

    public record SubmitIdentityRequest(
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotNull IssuingCountry issuingCountry,
            @NotBlank String documentNumber
    ) {}

    public record UploadUrlRequest(
            @NotNull DocumentType type
    ) {}

    public record UploadUrlResponse(
            UUID documentId,
            String uploadUrl
    ) {}

    public record DocumentSummary(
            UUID id,
            DocumentType type,
            boolean uploaded,
            LocalDateTime uploadedAt
    ) {}

    public record KycStatusResponse(
            UUID userId,
            KycStatus status,
            KycLevel level,
            String firstName,
            String lastName,
            IssuingCountry issuingCountry,
            String documentNumber,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt,
            String rejectionReason,
            List<DocumentSummary> documents
    ) {}

    // Used by other services (e.g. core-banking) to gate money-movement on KYC status without
    // pulling in identity detail they have no business seeing.
    public record KycEligibilityResponse(
            UUID userId,
            KycStatus status,
            KycLevel level
    ) {}
}
