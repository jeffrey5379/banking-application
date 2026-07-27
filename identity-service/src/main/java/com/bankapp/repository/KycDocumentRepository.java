package com.bankapp.repository;

import com.bankapp.model.kyc.DocumentType;
import com.bankapp.model.kyc.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {
    Optional<KycDocument> findByPublicId(UUID publicId);
    Optional<KycDocument> findByKycProfileIdAndType(Long kycProfileId, DocumentType type);
}
