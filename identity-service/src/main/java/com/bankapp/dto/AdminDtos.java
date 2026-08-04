package com.bankapp.dto;

import com.bankapp.model.kyc.KycStatus;

import java.util.UUID;

public class AdminDtos {

    public record AdminUserSummary(
            UUID id,
            String username,
            String email,
            boolean active,
            KycStatus kycStatus
    ) {}

    public record SetActiveRequest(
            boolean active
    ) {}
}
