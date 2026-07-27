package com.bankapp.model.kyc;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

// One row per (profile, document type) slot - reused/overwritten on re-upload rather than
// accumulating history, since only the latest upload of each type matters for verification.
@Entity
@Table(name = "kyc_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "publicId")
public class KycDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "kyc_profile_id", nullable = false)
    private KycProfile kycProfile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentType type;

    // Object key in the storage bucket - stable for the lifetime of this row, so a re-upload of
    // the same type just overwrites the existing object instead of leaving an orphaned one behind.
    @Column(nullable = false)
    private String storageKey;

    // Set only once the upload is confirmed to actually exist in the bucket (see
    // KycService#completeUpload) - null means "presigned URL issued but not confirmed yet".
    private LocalDateTime uploadedAt;

    public boolean isUploaded() {
        return uploadedAt != null;
    }
}
