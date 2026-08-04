package com.bankapp.service;

import com.bankapp.dto.AdminDtos.AdminUserSummary;
import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.Role;
import com.bankapp.model.User;
import com.bankapp.model.kyc.KycProfile;
import com.bankapp.model.kyc.KycStatus;
import com.bankapp.repository.KycProfileRepository;
import com.bankapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final KycProfileRepository kycProfileRepository;

    // Role.ADMIN/SYSTEM accounts (the one seeded admin, and "bank") are excluded - the console
    // manages customer accounts, not internal/privileged ones.
    @Transactional(readOnly = true)
    public List<AdminUserSummary> listUsers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.USER)
                .map(this::toSummary)
                .toList();
    }

    public AdminUserSummary setActive(UUID userId, boolean active) {
        User user = userRepository.findByPublicId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getRole() != Role.USER) {
            throw new IllegalArgumentException("Cannot change active status for a non-customer account");
        }
        user.setEnabled(active);
        userRepository.save(user);
        return toSummary(user);
    }

    private AdminUserSummary toSummary(User user) {
        KycStatus kycStatus = kycProfileRepository.findByUserId(user.getId())
                .map(KycProfile::getStatus)
                .orElse(KycStatus.NOT_STARTED);
        return new AdminUserSummary(user.getPublicId(), user.getUsername(), user.getEmail(),
                user.isEnabled(), kycStatus);
    }
}
