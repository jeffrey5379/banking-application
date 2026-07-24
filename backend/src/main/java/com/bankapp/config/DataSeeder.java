package com.bankapp.config;

import com.bankapp.dto.BankDtos.AccountSummaryResponse;
import com.bankapp.dto.BankDtos.CreateAccountRequest;
import com.bankapp.dto.BankDtos.RegisterRequest;
import com.bankapp.model.Account;
import com.bankapp.model.Currency;
import com.bankapp.model.ExchangeRate;
import com.bankapp.model.Operation;
import com.bankapp.model.OperationType;
import com.bankapp.repository.AccountRepository;
import com.bankapp.repository.ExchangeRateRepository;
import com.bankapp.repository.OperationRepository;
import com.bankapp.service.AccountService;
import com.bankapp.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private static final String BANK_USERNAME = "bank";

    private final AuthService authService;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final OperationRepository operationRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    @Override
    public void run(String... args) {
        log.info("Seeding initial data...");

        exchangeRateRepository.saveAll(List.of(
                new ExchangeRate(Currency.EUR, new BigDecimal("1.00000000")),
                new ExchangeRate(Currency.USD, new BigDecimal("0.92000000")),
                new ExchangeRate(Currency.CHF, new BigDecimal("1.05000000")),
                new ExchangeRate(Currency.GBP, new BigDecimal("1.17000000")),
                new ExchangeRate(Currency.SEK, new BigDecimal("0.08700000")),
                new ExchangeRate(Currency.VND, new BigDecimal("0.00003700"))
        ));

        // A fake "source of money" account, capitalized directly (the one place in the whole
        // system money is manufactured instead of moved). Every demo user's starting balance
        // arrives from here via a real double-entry transfer, instead of being credited out of
        // thin air - so the seeded data exercises the exact same ledger mechanics real transfers
        // use, and nobody's balance exists without a matching counter-entry somewhere.
        Long bankAccountId = seedBankAccount();

        authService.register(new RegisterRequest("alice", "alice@example.com", "alice123"));
        authService.register(new RegisterRequest("bob", "bob@example.com", "bob123"));
        authService.register(new RegisterRequest("carol", "carol@example.com", "carol123"));

        setCurrentUser("alice");
        var aliceEur = accountService.createAccount(new CreateAccountRequest(Currency.EUR));
        var aliceUsd = accountService.createAccount(new CreateAccountRequest(Currency.USD));
        var aliceGbp = accountService.createAccount(new CreateAccountRequest(Currency.GBP));

        // 50 backdated operations inserted directly to get meaningful chart data
        seedAliceEurHistory(bankAccountId, accountService.resolveAccountId(aliceEur.id()), aliceEur.username());

        fundFromBank(bankAccountId, aliceUsd, new BigDecimal("3000.00"), "Initial deposit USD");
        fundFromBank(bankAccountId, aliceGbp, new BigDecimal("2000.00"), "Transfer from UK");

        setCurrentUser("bob");
        var bobEur = accountService.createAccount(new CreateAccountRequest(Currency.EUR));
        var bobChf = accountService.createAccount(new CreateAccountRequest(Currency.CHF));
        fundFromBank(bankAccountId, bobEur, new BigDecimal("10000.00"), "Initial deposit");
        fundFromBank(bankAccountId, bobChf, new BigDecimal("4500.00"), "Swiss savings");

        setCurrentUser("carol");
        var carolUsd = accountService.createAccount(new CreateAccountRequest(Currency.USD));
        fundFromBank(bankAccountId, carolUsd, new BigDecimal("7000.00"), "Initial deposit");

        SecurityContextHolder.clearContext();
        log.info("Data seeding complete.");
    }

    private Long seedBankAccount() {
        authService.register(new RegisterRequest(BANK_USERNAME, "bank@bankapp.local", "bank-internal-seed"));
        setCurrentUser(BANK_USERNAME);
        var bankEur = accountService.createAccount(new CreateAccountRequest(Currency.EUR));
        Long bankAccountId = accountService.resolveAccountId(bankEur.id());

        Account bankAccount = accountRepository.findById(bankAccountId).orElseThrow();
        bankAccount.setBalance(new BigDecimal("1000000.00"));
        accountRepository.save(bankAccount);
        return bankAccountId;
    }

    // Moves seed money from the bank's reserve account to a freshly created user account via a
    // real double-entry transfer. Bypasses the debit-eligibility check and username/account-number
    // recipient verification that guard the public transfer() endpoint - not needed here since
    // both accounts are already resolved, trusted, internal ids (see AccountService.transferInternal).
    private void fundFromBank(Long bankAccountId, AccountSummaryResponse target, BigDecimal amount, String description) {
        Long targetAccountId = accountService.resolveAccountId(target.id());
        String outDesc = String.format("Transfer to %s (%s) - %s", target.username(), target.accountNumber(), description);
        accountService.transferInternal(bankAccountId, targetAccountId, amount, outDesc, description);
    }

    private void seedAliceEurHistory(Long bankAccountId, Long aliceAccountId, String aliceUsername) {
        Account alice = accountRepository.findById(aliceAccountId).orElseThrow();
        Account bank = accountRepository.findById(bankAccountId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();

        record SeedOp(int daysAgo, String desc, String amount, OperationType type) {
        }

        var ops = List.of(
                new SeedOp(180, "Initial deposit", "5000.00", OperationType.TRANSFER_IN),
                new SeedOp(175, "Salary", "3200.00", OperationType.TRANSFER_IN),
                new SeedOp(170, "Rent", "1500.00", OperationType.TRANSFER_OUT),
                new SeedOp(168, "Groceries", "180.00", OperationType.TRANSFER_OUT),
                new SeedOp(165, "Utilities", "120.00", OperationType.TRANSFER_OUT),
                new SeedOp(162, "Coffee shop", "28.00", OperationType.TRANSFER_OUT),
                new SeedOp(158, "Performance bonus", "800.00", OperationType.TRANSFER_IN),
                new SeedOp(155, "Restaurant", "95.00", OperationType.TRANSFER_OUT),
                new SeedOp(152, "Transport", "60.00", OperationType.TRANSFER_OUT),
                new SeedOp(148, "Pharmacy", "35.00", OperationType.TRANSFER_OUT),
                new SeedOp(145, "Salary", "3200.00", OperationType.TRANSFER_IN),
                new SeedOp(140, "Rent", "1500.00", OperationType.TRANSFER_OUT),
                new SeedOp(138, "Groceries", "165.00", OperationType.TRANSFER_OUT),
                new SeedOp(135, "Utilities", "118.00", OperationType.TRANSFER_OUT),
                new SeedOp(132, "Gym membership", "45.00", OperationType.TRANSFER_OUT),
                new SeedOp(128, "Clothing", "250.00", OperationType.TRANSFER_OUT),
                new SeedOp(125, "Restaurant", "78.00", OperationType.TRANSFER_OUT),
                new SeedOp(120, "Coffee shop", "32.00", OperationType.TRANSFER_OUT),
                new SeedOp(118, "Transport", "55.00", OperationType.TRANSFER_OUT),
                new SeedOp(115, "Books", "89.00", OperationType.TRANSFER_OUT),
                new SeedOp(112, "Salary", "3200.00", OperationType.TRANSFER_IN),
                new SeedOp(108, "Rent", "1500.00", OperationType.TRANSFER_OUT),
                new SeedOp(105, "Vacation", "1200.00", OperationType.TRANSFER_OUT),
                new SeedOp(102, "Groceries", "142.00", OperationType.TRANSFER_OUT),
                new SeedOp(98, "Utilities", "110.00", OperationType.TRANSFER_OUT),
                new SeedOp(95, "Restaurant", "125.00", OperationType.TRANSFER_OUT),
                new SeedOp(92, "Coffee shop", "29.00", OperationType.TRANSFER_OUT),
                new SeedOp(88, "Gift shop", "175.00", OperationType.TRANSFER_OUT),
                new SeedOp(85, "Transport", "48.00", OperationType.TRANSFER_OUT),
                new SeedOp(82, "Salary", "3200.00", OperationType.TRANSFER_IN),
                new SeedOp(78, "Rent", "1500.00", OperationType.TRANSFER_OUT),
                new SeedOp(75, "Groceries", "155.00", OperationType.TRANSFER_OUT),
                new SeedOp(72, "Utilities", "115.00", OperationType.TRANSFER_OUT),
                new SeedOp(68, "Concert tickets", "180.00", OperationType.TRANSFER_OUT),
                new SeedOp(65, "Restaurant", "92.00", OperationType.TRANSFER_OUT),
                new SeedOp(62, "Coffee shop", "31.00", OperationType.TRANSFER_OUT),
                new SeedOp(58, "Electronics", "450.00", OperationType.TRANSFER_OUT),
                new SeedOp(55, "Transport", "52.00", OperationType.TRANSFER_OUT),
                new SeedOp(52, "Freelance income", "1500.00", OperationType.TRANSFER_IN),
                new SeedOp(48, "Salary", "3200.00", OperationType.TRANSFER_IN),
                new SeedOp(45, "Rent", "1500.00", OperationType.TRANSFER_OUT),
                new SeedOp(42, "Groceries", "170.00", OperationType.TRANSFER_OUT),
                new SeedOp(38, "Utilities", "122.00", OperationType.TRANSFER_OUT),
                new SeedOp(35, "Restaurant", "88.00", OperationType.TRANSFER_OUT),
                new SeedOp(32, "Coffee shop", "27.00", OperationType.TRANSFER_OUT),
                new SeedOp(28, "Insurance", "320.00", OperationType.TRANSFER_OUT),
                new SeedOp(25, "Transport", "45.00", OperationType.TRANSFER_OUT),
                new SeedOp(22, "Groceries", "148.00", OperationType.TRANSFER_OUT),
                new SeedOp(15, "Salary", "3200.00", OperationType.TRANSFER_IN),
                new SeedOp(5, "Rent", "1500.00", OperationType.TRANSFER_OUT)
        );

        BigDecimal aliceBalance = BigDecimal.ZERO;
        BigDecimal bankBalance = bank.getBalance();

        for (SeedOp op : ops) {
            BigDecimal amount = new BigDecimal(op.amount());
            boolean aliceReceives = op.type() == OperationType.TRANSFER_IN;
            LocalDateTime createdAt = now.minusDays(op.daysAgo());

            aliceBalance = aliceReceives ? aliceBalance.add(amount) : aliceBalance.subtract(amount);
            bankBalance = aliceReceives ? bankBalance.subtract(amount) : bankBalance.add(amount);

            // Alice's side keeps the natural, human-readable description (salary, rent, ...).
            Operation aliceOp = seedOperation(alice, op.type(),
                    amount, aliceBalance, op.desc(), createdAt, bank.getId());
            operationRepository.save(aliceOp);

            // Bank's side mirrors the same convention transfer() itself uses for descriptions.
            String bankDesc = aliceReceives
                    ? String.format("Transfer to %s (%s) - %s", aliceUsername, alice.getAccountNumber(), op.desc())
                    : String.format("Transfer from %s (%s) - %s", aliceUsername, alice.getAccountNumber(), op.desc());
            Operation bankOp = seedOperation(bank, aliceReceives ? OperationType.TRANSFER_OUT : OperationType.TRANSFER_IN,
                    amount, bankBalance, bankDesc, createdAt, alice.getId());
            operationRepository.save(bankOp);
        }

        alice.setBalance(aliceBalance);
        accountRepository.save(alice);
        bank.setBalance(bankBalance);
        accountRepository.save(bank);
        log.info("Seeded 50 paired operations for alice EUR account, final balance: {}", aliceBalance);
    }

    private Operation seedOperation(Account account, OperationType type, BigDecimal amount,
                                    BigDecimal balanceAfter, String description, LocalDateTime createdAt,
                                    Long relatedAccountId) {
        Operation operation = new Operation();
        operation.setAccount(account);
        operation.setType(type);
        operation.setAmount(amount);
        operation.setCurrency(Currency.EUR);
        operation.setBalanceAfter(balanceAfter);
        operation.setDescription(description);
        operation.setCreatedAt(createdAt);
        operation.setExchangeRate(BigDecimal.ONE);
        operation.setRelatedAccountId(relatedAccountId);
        return operation;
    }

    private void setCurrentUser(String username) {
        UserDetails userDetails = User.builder()
                .username(username)
                .password("")
                .roles("USER")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }
}
