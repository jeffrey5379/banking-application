package com.bankapp.model.kyc;

// BASIC unlocks normal account use; ENHANCED is what higher-risk operations (e.g. large
// transfers) would require - core-banking decides what to gate on which level, this service
// only ever reports what level (if any) a user has reached.
public enum KycLevel {
    NONE, BASIC, ENHANCED
}
