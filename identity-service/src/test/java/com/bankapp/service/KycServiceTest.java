package com.bankapp.service;

import com.bankapp.dto.KycDtos.KycEligibilityResponse;
import com.bankapp.dto.KycDtos.KycStatusResponse;
import com.bankapp.dto.KycDtos.SubmitIdentityRequest;
import com.bankapp.dto.KycDtos.UploadUrlRequest;
import com.bankapp.dto.KycDtos.UploadUrlResponse;
import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.User;
import com.bankapp.model.kyc.DocumentType;
import com.bankapp.model.kyc.IssuingCountry;
import com.bankapp.model.kyc.KycDocument;
import com.bankapp.model.kyc.KycLevel;
import com.bankapp.model.kyc.KycProfile;
import com.bankapp.model.kyc.KycStatus;
import com.bankapp.repository.KycDocumentRepository;
import com.bankapp.repository.KycProfileRepository;
import com.bankapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KycServiceTest {

    private static final String BUCKET = "kyc-test-bucket";

    @Mock private KycProfileRepository kycProfileRepository;
    @Mock private KycDocumentRepository kycDocumentRepository;
    @Mock private UserRepository userRepository;
    @Mock private S3Presigner s3Presigner;
    @Mock private S3Client s3Client;

    private KycService kycService;

    private User alice;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        alice.setEmail("alice@example.com");

        kycService = new KycService(kycProfileRepository, kycDocumentRepository, userRepository,
                s3Presigner, s3Client, BUCKET);

        when(kycProfileRepository.save(any(KycProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(kycDocumentRepository.save(any(KycDocument.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── getStatus ────────────────────────────────────────────────────────────

    @Test
    void getStatus_noProfileYet_createsAndReturnsNotStarted() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        KycStatusResponse result = kycService.getStatus("alice");

        assertThat(result.status()).isEqualTo(KycStatus.NOT_STARTED);
        assertThat(result.level()).isEqualTo(KycLevel.NONE);
        assertThat(result.firstName()).isNull();
        assertThat(result.documents()).isEmpty();
    }

    @Test
    void getStatus_unknownUsername_throwsResourceNotFoundException() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.getStatus("nobody"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── submitIdentity ───────────────────────────────────────────────────────

    @Test
    void submitIdentity_validDetails_setsPendingNotVerified() {
        KycProfile profile = freshProfile();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        KycStatusResponse result = kycService.submitIdentity("alice",
                new SubmitIdentityRequest("Alice", "Anderson", IssuingCountry.UNITED_KINGDOM, "AA1234567"));

        assertThat(result.status()).isEqualTo(KycStatus.PENDING);
        assertThat(result.level()).isEqualTo(KycLevel.NONE);
        assertThat(result.firstName()).isEqualTo("Alice");
        assertThat(result.lastName()).isEqualTo("Anderson");
        assertThat(result.issuingCountry()).isEqualTo(IssuingCountry.UNITED_KINGDOM);
        assertThat(result.documentNumber()).isEqualTo("AA1234567");
    }

    @Test
    void submitIdentity_rejectedProfileResubmits_clearsRejectionReasonAndReturnsToPending() {
        KycProfile profile = freshProfile();
        profile.setStatus(KycStatus.REJECTED);
        profile.setRejectionReason("Details did not match");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        KycStatusResponse result = kycService.submitIdentity("alice",
                new SubmitIdentityRequest("Alice", "Anderson", IssuingCountry.UNITED_KINGDOM, "AA1234567"));

        assertThat(result.status()).isEqualTo(KycStatus.PENDING);
        assertThat(result.rejectionReason()).isNull();
    }

    @Test
    void submitIdentity_bothDocumentsAlreadyUploaded_autoVerifiesToBasic() {
        KycProfile profile = freshProfile();
        profile.getDocuments().add(uploadedDocument(profile, DocumentType.ID_DOCUMENT));
        profile.getDocuments().add(uploadedDocument(profile, DocumentType.SELFIE));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        KycStatusResponse result = kycService.submitIdentity("alice",
                new SubmitIdentityRequest("Alice", "Anderson", IssuingCountry.UNITED_KINGDOM, "AA1234567"));

        assertThat(result.status()).isEqualTo(KycStatus.VERIFIED);
        assertThat(result.level()).isEqualTo(KycLevel.BASIC);
        assertThat(result.reviewedAt()).isNotNull();
    }

    // ── requestUploadUrl ─────────────────────────────────────────────────────

    @Test
    void requestUploadUrl_identityNotSubmittedYet_throwsIllegalArgumentException() {
        KycProfile profile = freshProfile();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> kycService.requestUploadUrl("alice", new UploadUrlRequest(DocumentType.ID_DOCUMENT)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestUploadUrl_validRequest_createsDocumentAndReturnsPresignedUrl() {
        KycProfile profile = freshProfile();
        profile.setFirstName("Alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(kycDocumentRepository.findByKycProfileIdAndType(10L, DocumentType.ID_DOCUMENT))
                .thenReturn(Optional.empty());
        when(s3Presigner.presignPutObject(any(Consumer.class)))
                .thenReturn(fakePresignedRequest("https://minio.local/" + BUCKET + "/some-key?X-Amz-Signature=abc"));

        UploadUrlResponse result = kycService.requestUploadUrl("alice", new UploadUrlRequest(DocumentType.ID_DOCUMENT));

        assertThat(result.documentId()).isNotNull();
        assertThat(result.uploadUrl()).isEqualTo("https://minio.local/" + BUCKET + "/some-key?X-Amz-Signature=abc");
    }

    @Test
    void requestUploadUrl_existingDocumentOfSameType_reusesTheSameRowAndResetsUploadedAt() {
        KycProfile profile = freshProfile();
        profile.setFirstName("Alice");
        KycDocument existing = uploadedDocument(profile, DocumentType.ID_DOCUMENT);
        UUID existingPublicId = existing.getPublicId();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(kycDocumentRepository.findByKycProfileIdAndType(10L, DocumentType.ID_DOCUMENT))
                .thenReturn(Optional.of(existing));
        when(s3Presigner.presignPutObject(any(Consumer.class)))
                .thenReturn(fakePresignedRequest("https://minio.local/" + BUCKET + "/existing-key"));

        UploadUrlResponse result = kycService.requestUploadUrl("alice", new UploadUrlRequest(DocumentType.ID_DOCUMENT));

        assertThat(result.documentId()).isEqualTo(existingPublicId);
        assertThat(existing.isUploaded()).isFalse();
    }

    // ── completeUpload ───────────────────────────────────────────────────────

    @Test
    void completeUpload_unknownDocumentId_throwsResourceNotFoundException() {
        UUID unknown = UUID.randomUUID();
        when(kycDocumentRepository.findByPublicId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.completeUpload("alice", unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void completeUpload_documentBelongsToAnotherUser_throwsAccessDeniedException() {
        User bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");
        KycProfile bobProfile = new KycProfile();
        bobProfile.setId(20L);
        bobProfile.setUser(bob);
        KycDocument bobDocument = new KycDocument();
        bobDocument.setKycProfile(bobProfile);
        bobDocument.setType(DocumentType.ID_DOCUMENT);
        bobDocument.setStorageKey("kyc/bob/id");
        when(kycDocumentRepository.findByPublicId(bobDocument.getPublicId())).thenReturn(Optional.of(bobDocument));

        assertThatThrownBy(() -> kycService.completeUpload("alice", bobDocument.getPublicId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void completeUpload_objectNotInStorageYet_throwsIllegalArgumentException() {
        KycProfile profile = freshProfile();
        profile.setFirstName("Alice");
        KycDocument document = new KycDocument();
        document.setKycProfile(profile);
        document.setType(DocumentType.ID_DOCUMENT);
        document.setStorageKey("kyc/alice/id");
        when(kycDocumentRepository.findByPublicId(document.getPublicId())).thenReturn(Optional.of(document));
        when(s3Client.headObject(any(software.amazon.awssdk.services.s3.model.HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        assertThatThrownBy(() -> kycService.completeUpload("alice", document.getPublicId()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(document.isUploaded()).isFalse();
    }

    @Test
    void completeUpload_secondOfTwoRequiredDocuments_autoVerifiesToBasic() {
        KycProfile profile = freshProfile();
        profile.setFirstName("Alice");
        profile.setStatus(KycStatus.PENDING);
        profile.getDocuments().add(uploadedDocument(profile, DocumentType.ID_DOCUMENT));

        KycDocument selfie = new KycDocument();
        selfie.setKycProfile(profile);
        selfie.setType(DocumentType.SELFIE);
        selfie.setStorageKey("kyc/alice/selfie");
        profile.getDocuments().add(selfie);

        when(kycDocumentRepository.findByPublicId(selfie.getPublicId())).thenReturn(Optional.of(selfie));

        KycStatusResponse result = kycService.completeUpload("alice", selfie.getPublicId());

        assertThat(selfie.isUploaded()).isTrue();
        assertThat(result.status()).isEqualTo(KycStatus.VERIFIED);
        assertThat(result.level()).isEqualTo(KycLevel.BASIC);
    }

    @Test
    void completeUpload_onlyOneOfTwoRequiredDocuments_staysPending() {
        KycProfile profile = freshProfile();
        profile.setFirstName("Alice");
        profile.setStatus(KycStatus.PENDING);

        KycDocument selfie = new KycDocument();
        selfie.setKycProfile(profile);
        selfie.setType(DocumentType.SELFIE);
        selfie.setStorageKey("kyc/alice/selfie");
        profile.getDocuments().add(selfie);
        // No ID_DOCUMENT uploaded at all.

        when(kycDocumentRepository.findByPublicId(selfie.getPublicId())).thenReturn(Optional.of(selfie));

        KycStatusResponse result = kycService.completeUpload("alice", selfie.getPublicId());

        assertThat(selfie.isUploaded()).isTrue();
        assertThat(result.status()).isEqualTo(KycStatus.PENDING);
    }

    // ── seedCompletedDocument ────────────────────────────────────────────────

    @Test
    void seedCompletedDocument_marksUploadedWithoutTouchingStorage() {
        KycProfile profile = freshProfile();
        profile.setFirstName("Alice");
        profile.getDocuments().add(uploadedDocument(profile, DocumentType.SELFIE));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(kycDocumentRepository.findByKycProfileIdAndType(10L, DocumentType.ID_DOCUMENT))
                .thenReturn(Optional.empty());

        kycService.seedCompletedDocument("alice", DocumentType.ID_DOCUMENT);

        verifyNoInteractions(s3Client, s3Presigner);
        ArgumentCaptor<KycDocument> captor = ArgumentCaptor.forClass(KycDocument.class);
        verify(kycDocumentRepository, atLeastOnce()).save(captor.capture());
        assertThat(captor.getValue().isUploaded()).isTrue();
        assertThat(profile.getStatus()).isEqualTo(KycStatus.VERIFIED);
    }

    // ── getEligibility ───────────────────────────────────────────────────────

    @Test
    void getEligibility_noProfile_returnsNotStartedNone() {
        UUID publicId = alice.getPublicId();
        when(userRepository.findByPublicId(publicId)).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        KycEligibilityResponse result = kycService.getEligibility(publicId);

        assertThat(result.status()).isEqualTo(KycStatus.NOT_STARTED);
        assertThat(result.level()).isEqualTo(KycLevel.NONE);
    }

    @Test
    void getEligibility_verifiedProfile_returnsVerifiedBasic() {
        UUID publicId = alice.getPublicId();
        KycProfile profile = freshProfile();
        profile.setStatus(KycStatus.VERIFIED);
        profile.setLevel(KycLevel.BASIC);
        when(userRepository.findByPublicId(publicId)).thenReturn(Optional.of(alice));
        when(kycProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));

        KycEligibilityResponse result = kycService.getEligibility(publicId);

        assertThat(result.status()).isEqualTo(KycStatus.VERIFIED);
        assertThat(result.level()).isEqualTo(KycLevel.BASIC);
    }

    @Test
    void getEligibility_unknownUser_throwsResourceNotFoundException() {
        UUID unknown = UUID.randomUUID();
        when(userRepository.findByPublicId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.getEligibility(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private KycProfile freshProfile() {
        KycProfile profile = new KycProfile();
        profile.setId(10L);
        profile.setUser(alice);
        return profile;
    }

    private KycDocument uploadedDocument(KycProfile profile, DocumentType type) {
        KycDocument document = new KycDocument();
        document.setKycProfile(profile);
        document.setType(type);
        document.setStorageKey("kyc/alice/" + type);
        document.setUploadedAt(LocalDateTime.now());
        return document;
    }

    private static PresignedPutObjectRequest fakePresignedRequest(String url) {
        SdkHttpRequest httpRequest = SdkHttpRequest.builder()
                .method(SdkHttpMethod.PUT)
                .uri(URI.create(url))
                .build();
        return PresignedPutObjectRequest.builder()
                .expiration(Instant.now().plusSeconds(900))
                .isBrowserExecutable(true)
                .signedHeaders(Map.of("host", java.util.List.of("minio.local")))
                .httpRequest(httpRequest)
                .build();
    }
}
