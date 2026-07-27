package com.bankapp.repository;

import com.bankapp.model.kyc.KycProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KycProfileRepository extends JpaRepository<KycProfile, Long> {
    Optional<KycProfile> findByUserId(Long userId);
    Optional<KycProfile> findByUserUsername(String username);
}
