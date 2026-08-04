package com.bankapp.service;

import com.bankapp.dto.KycDtos.DocumentSummary;
import com.bankapp.dto.KycDtos.KycEligibilityResponse;
import com.bankapp.dto.KycDtos.KycStatusResponse;
import com.bankapp.dto.KycDtos.SubmitIdentityRequest;
import com.bankapp.dto.KycDtos.UploadUrlRequest;
import com.bankapp.dto.KycDtos.UploadUrlResponse;
import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.User;
import com.bankapp.model.kyc.DocumentType;
import com.bankapp.model.kyc.KycDocument;
import com.bankapp.model.kyc.KycLevel;
import com.bankapp.model.kyc.KycProfile;
import com.bankapp.model.kyc.KycStatus;
import com.bankapp.repository.KycDocumentRepository;
import com.bankapp.repository.KycProfileRepository;
import com.bankapp.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class KycService {

    private static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(15);

    private final KycProfileRepository kycProfileRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final UserRepository userRepository;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;
    private final String bucket;

    public KycService(KycProfileRepository kycProfileRepository,
                       KycDocumentRepository kycDocumentRepository,
                       UserRepository userRepository,
                       S3Presigner s3Presigner,
                       S3Client s3Client,
                       @Value("${kyc.storage.bucket}") String bucket) {
        this.kycProfileRepository = kycProfileRepository;
        this.kycDocumentRepository = kycDocumentRepository;
        this.userRepository = userRepository;
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    // Not readOnly: getOrCreateProfile() lazily inserts a KycProfile on first access.
    @Transactional
    public KycStatusResponse getStatus(String username) {
        return toStatusResponse(getOrCreateProfile(username));
    }

    public KycStatusResponse submitIdentity(String username, SubmitIdentityRequest req) {
        KycProfile profile = getOrCreateProfile(username);

        profile.setFirstName(req.firstName());
        profile.setLastName(req.lastName());
        profile.setIssuingCountry(req.issuingCountry());
        profile.setDocumentNumber(req.documentNumber());

        if (profile.getSubmittedAt() == null) {
            profile.setSubmittedAt(LocalDateTime.now());
        }
        maybeMoveToPending(profile);
        kycProfileRepository.save(profile);

        return toStatusResponse(profile);
    }

    // Issues a presigned S3/MinIO PUT URL the browser uploads directly to, so the file bytes
    // never pass through this service. A document row is created/reset up front (uploadedAt =
    // null) so completeUpload() has something to confirm against and there's a stable storageKey
    // to hand out again if the same upload is retried.
    public UploadUrlResponse requestUploadUrl(String username, UploadUrlRequest req) {
        KycProfile profile = getOrCreateProfile(username);
        if (profile.getFirstName() == null) {
            throw new IllegalArgumentException("Submit your identity details before uploading documents");
        }

        KycDocument document = kycDocumentRepository.findByKycProfileIdAndType(profile.getId(), req.type())
                .orElseGet(() -> {
                    KycDocument doc = new KycDocument();
                    doc.setKycProfile(profile);
                    doc.setType(req.type());
                    doc.setStorageKey("kyc/" + profile.getPublicId() + "/" + doc.getPublicId() + "/" + req.type());
                    // Keep the in-memory collection consistent with what we just created rather
                    // than relying on a later lazy (re)load to pick it up.
                    profile.getDocuments().add(doc);
                    return doc;
                });
        document.setUploadedAt(null);
        document = kycDocumentRepository.save(document);

        String uploadUrl = presignUpload(document.getStorageKey());
        return new UploadUrlResponse(document.getPublicId(), uploadUrl);
    }

    // Confirms the object actually landed in storage (via a real headObject call)
    public KycStatusResponse completeUpload(String username, UUID documentId) {
        KycDocument document = kycDocumentRepository.findByPublicId(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        KycProfile profile = document.getKycProfile();
        if (!profile.getUser().getUsername().equals(username)) {
            throw new AccessDeniedException("This document does not belong to you");
        }

        if (!objectExists(document.getStorageKey())) {
            throw new IllegalArgumentException("Upload not found yet - please retry the upload");
        }
        document.setUploadedAt(LocalDateTime.now());
        kycDocumentRepository.save(document);

        maybeMoveToPending(profile);
        kycProfileRepository.save(profile);

        return toStatusResponse(profile);
    }

    @Transactional(readOnly = true)
    public KycEligibilityResponse getEligibility(UUID userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userPublicId));
        KycProfile profile = kycProfileRepository.findByUserId(user.getId())
                .orElse(null);
        KycStatus status = profile != null ? profile.getStatus() : KycStatus.NOT_STARTED;
        KycLevel level = profile != null ? profile.getLevel() : KycLevel.NONE;
        return new KycEligibilityResponse(userPublicId, status, level);
    }

    // Seed-only convenience: DataSeeder calls this to mark documents as uploaded without ever
    // touching S3/MinIO, mirroring how it seeds already-VERIFIED demo users without going through
    // the real upload flow.
    public void seedCompletedDocument(String username, DocumentType type) {
        KycProfile profile = getOrCreateProfile(username);
        KycDocument document = kycDocumentRepository.findByKycProfileIdAndType(profile.getId(), type)
                .orElseGet(() -> {
                    KycDocument doc = new KycDocument();
                    doc.setKycProfile(profile);
                    doc.setType(type);
                    doc.setStorageKey("seed/" + type);
                    profile.getDocuments().add(doc);
                    return doc;
                });
        document.setUploadedAt(LocalDateTime.now());
        kycDocumentRepository.save(document);
        tryAutoVerify(profile);
    }

    private void tryAutoVerify(KycProfile profile) {
        if (profile.getStatus() == KycStatus.VERIFIED) {
            return;
        }
        if (isComplete(profile)) {
            profile.setStatus(KycStatus.VERIFIED);
            profile.setLevel(KycLevel.BASIC);
            profile.setReviewedAt(LocalDateTime.now());
            profile.setRejectionReason(null);
            kycProfileRepository.save(profile);
        }
    }

    private void maybeMoveToPending(KycProfile profile) {
        if ((profile.getStatus() == KycStatus.NOT_STARTED || profile.getStatus() == KycStatus.REJECTED)
                && isComplete(profile)) {
            profile.setStatus(KycStatus.PENDING);
            profile.setRejectionReason(null);
        }
    }

    private boolean isComplete(KycProfile profile) {
        return profile.getFirstName() != null
                && profile.getLastName() != null
                && profile.getIssuingCountry() != null
                && profile.getDocumentNumber() != null
                && isTypeUploaded(profile, DocumentType.ID_DOCUMENT)
                && isTypeUploaded(profile, DocumentType.SELFIE);
    }

    private boolean isTypeUploaded(KycProfile profile, DocumentType type) {
        return profile.getDocuments().stream().anyMatch(d -> d.getType() == type && d.isUploaded());
    }

    private String presignUpload(String storageKey) {
        PutObjectRequest putRequest = PutObjectRequest.builder().bucket(bucket).key(storageKey).build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(b -> b
                .signatureDuration(UPLOAD_URL_TTL)
                .putObjectRequest(putRequest));
        return presigned.url().toString();
    }

    private boolean objectExists(String storageKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(storageKey).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private KycProfile getOrCreateProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return kycProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    KycProfile profile = new KycProfile();
                    profile.setUser(user);
                    return kycProfileRepository.save(profile);
                });
    }

    private KycStatusResponse toStatusResponse(KycProfile profile) {
        List<DocumentSummary> documents = profile.getDocuments().stream()
                .map(d -> new DocumentSummary(d.getPublicId(), d.getType(), d.isUploaded(), d.getUploadedAt()))
                .toList();
        return new KycStatusResponse(profile.getUser().getPublicId(), profile.getStatus(), profile.getLevel(),
                profile.getFirstName(), profile.getLastName(), profile.getIssuingCountry(), profile.getDocumentNumber(),
                profile.getSubmittedAt(), profile.getReviewedAt(), profile.getRejectionReason(), documents);
    }
}
