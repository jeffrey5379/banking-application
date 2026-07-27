package com.bankapp.config;

import com.bankapp.dto.KycDtos.SubmitIdentityRequest;
import com.bankapp.model.User;
import com.bankapp.model.kyc.DocumentType;
import com.bankapp.model.kyc.IssuingCountry;
import com.bankapp.repository.UserRepository;
import com.bankapp.service.KycService;
import com.bankapp.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    // The "bank" account is a real, fully modelled user (core-banking transfers real money out
    // of its account) but login for it is disabled - see UserService/GlobalExceptionHandler.
    private static final List<String[]> SEED_USERS = List.of(
            new String[]{"alice", "alice@example.com", "alice123"},
            new String[]{"bob", "bob@example.com", "bob123"},
            new String[]{"carol", "carol@example.com", "carol123"},
            new String[]{"bank", "bank@example.com", "bank-internal-seed"}
    );

    // firstName, lastName, issuingCountry, documentNumber - keyed by index into SEED_USERS.
    private static final List<SubmitIdentityRequest> SEED_IDENTITIES = List.of(
            new SubmitIdentityRequest("Alice", "Anderson", IssuingCountry.UNITED_KINGDOM, "AA1234567"),
            new SubmitIdentityRequest("Bob", "Baker", IssuingCountry.UNITED_STATES, "BB7654321"),
            new SubmitIdentityRequest("Carol", "Carter", IssuingCountry.CANADA, "CC1928374"),
            new SubmitIdentityRequest("Bank", "Systems", IssuingCountry.GERMANY, "BANK0000001")
    );

    private final UserService userService;
    private final KycService kycService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("Seeding identity data...");

        for (int i = 0; i < SEED_USERS.size(); i++) {
            String[] u = SEED_USERS.get(i);
            String username = u[0], email = u[1], password = u[2];

            // Idempotency matters here: H2 (dev) is wiped on every restart so this check never
            // trips, but prod's Postgres persists across container restarts/redeploys - without
            // this guard, any restart after a partially-successful run (e.g. the user got
            // created but a later step in the loop threw) would hit a duplicate-username
            // conflict on createUser() and crash the whole app on every subsequent boot.
            if (userRepository.findByUsername(username).isPresent()) {
                log.info("{} already seeded, skipping.", username);
                continue;
            }

            // Creates the user directly rather than going through AuthService.register() - the
            // latter always sends a real OTP email (same two-step flow real signups go through),
            // which seeding neither needs nor should depend on: nothing here ever calls
            // verifyOtp() to consume that challenge, and these users are separately marked
            // verified via the KYC seeding below, unrelated to the OTP/session flow.
            userService.createUser(username, email, passwordEncoder.encode(password));

            // Auto-verify the seeded demo users so the rest of the demo (account creation,
            // transfers) works out of the box. A real new signup starts NOT_STARTED and has to
            // go through /api/kyc/identity plus document/selfie upload like everyone else - this
            // is seed-only convenience, bypassing S3/MinIO entirely rather than requiring it up
            // just to start identity-service.
            kycService.submitIdentity(username, SEED_IDENTITIES.get(i));
            kycService.seedCompletedDocument(username, DocumentType.ID_DOCUMENT);
            kycService.seedCompletedDocument(username, DocumentType.SELFIE);
        }

        User bank = userRepository.findByUsername("bank").orElseThrow();
        bank.setEnabled(false);
        userRepository.save(bank);

        log.info("Identity seeding complete.");
    }
}
