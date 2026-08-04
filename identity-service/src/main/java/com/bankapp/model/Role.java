package com.bankapp.model;

// USER: normal customer account. ADMIN: can access the admin console (/api/admin/**). SYSTEM:
// internal-only account (e.g. "bank", the money-seeding reserve) - never a real end user, so it's
// excluded from the admin console's user list regardless of its enabled flag.
public enum Role {
    USER, ADMIN, SYSTEM
}
