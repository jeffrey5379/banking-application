package com.bankapp.config;

import com.bankapp.dto.BankDtos.CreateAccountRequest;
import com.bankapp.dto.BankDtos.MoneyRequest;
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

        authService.register(new RegisterRequest("alice", "alice@example.com", "alice123"));
        authService.register(new RegisterRequest("bob", "bob@example.com", "bob123"));
        authService.register(new RegisterRequest("carol", "carol@example.com", "carol123"));

        setCurrentUser("alice");
        var aliceEur = accountService.createAccount(new CreateAccountRequest(Currency.EUR));
        var aliceUsd = accountService.createAccount(new CreateAccountRequest(Currency.USD));
        var aliceGbp = accountService.createAccount(new CreateAccountRequest(Currency.GBP));

        // 50 backdated operations inserted directly to get meaningful chart data
        seedAliceEurHistory(aliceEur.id());

        accountService.credit(aliceUsd.id(), new MoneyRequest(new BigDecimal("3000.00"), "Initial deposit USD"));
        accountService.credit(aliceGbp.id(), new MoneyRequest(new BigDecimal("2000.00"), "Transfer from UK"));

        setCurrentUser("bob");
        var bobEur = accountService.createAccount(new CreateAccountRequest(Currency.EUR));
        var bobChf = accountService.createAccount(new CreateAccountRequest(Currency.CHF));
        accountService.credit(bobEur.id(), new MoneyRequest(new BigDecimal("10000.00"), "Initial deposit"));
        accountService.credit(bobChf.id(), new MoneyRequest(new BigDecimal("4500.00"), "Swiss savings"));

        setCurrentUser("carol");
        var carolUsd = accountService.createAccount(new CreateAccountRequest(Currency.USD));
        accountService.credit(carolUsd.id(), new MoneyRequest(new BigDecimal("7000.00"), "Initial deposit"));

        SecurityContextHolder.clearContext();
        log.info("Data seeding complete.");
    }

    private void seedAliceEurHistory(Long accountId) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();

        record SeedOp(int daysAgo, String desc, String amount, OperationType type) {
        }

        var ops = List.of(
                new SeedOp(180, "Initial deposit", "5000.00", OperationType.CREDIT),
                new SeedOp(175, "Salary", "3200.00", OperationType.CREDIT),
                new SeedOp(170, "Rent", "1500.00", OperationType.DEBIT),
                new SeedOp(168, "Groceries", "180.00", OperationType.DEBIT),
                new SeedOp(165, "Utilities", "120.00", OperationType.DEBIT),
                new SeedOp(162, "Coffee shop", "28.00", OperationType.DEBIT),
                new SeedOp(158, "Performance bonus", "800.00", OperationType.CREDIT),
                new SeedOp(155, "Restaurant", "95.00", OperationType.DEBIT),
                new SeedOp(152, "Transport", "60.00", OperationType.DEBIT),
                new SeedOp(148, "Pharmacy", "35.00", OperationType.DEBIT),
                new SeedOp(145, "Salary", "3200.00", OperationType.CREDIT),
                new SeedOp(140, "Rent", "1500.00", OperationType.DEBIT),
                new SeedOp(138, "Groceries", "165.00", OperationType.DEBIT),
                new SeedOp(135, "Utilities", "118.00", OperationType.DEBIT),
                new SeedOp(132, "Gym membership", "45.00", OperationType.DEBIT),
                new SeedOp(128, "Clothing", "250.00", OperationType.DEBIT),
                new SeedOp(125, "Restaurant", "78.00", OperationType.DEBIT),
                new SeedOp(120, "Coffee shop", "32.00", OperationType.DEBIT),
                new SeedOp(118, "Transport", "55.00", OperationType.DEBIT),
                new SeedOp(115, "Books", "89.00", OperationType.DEBIT),
                new SeedOp(112, "Salary", "3200.00", OperationType.CREDIT),
                new SeedOp(108, "Rent", "1500.00", OperationType.DEBIT),
                new SeedOp(105, "Vacation", "1200.00", OperationType.DEBIT),
                new SeedOp(102, "Groceries", "142.00", OperationType.DEBIT),
                new SeedOp(98, "Utilities", "110.00", OperationType.DEBIT),
                new SeedOp(95, "Restaurant", "125.00", OperationType.DEBIT),
                new SeedOp(92, "Coffee shop", "29.00", OperationType.DEBIT),
                new SeedOp(88, "Gift shop", "175.00", OperationType.DEBIT),
                new SeedOp(85, "Transport", "48.00", OperationType.DEBIT),
                new SeedOp(82, "Salary", "3200.00", OperationType.CREDIT),
                new SeedOp(78, "Rent", "1500.00", OperationType.DEBIT),
                new SeedOp(75, "Groceries", "155.00", OperationType.DEBIT),
                new SeedOp(72, "Utilities", "115.00", OperationType.DEBIT),
                new SeedOp(68, "Concert tickets", "180.00", OperationType.DEBIT),
                new SeedOp(65, "Restaurant", "92.00", OperationType.DEBIT),
                new SeedOp(62, "Coffee shop", "31.00", OperationType.DEBIT),
                new SeedOp(58, "Electronics", "450.00", OperationType.DEBIT),
                new SeedOp(55, "Transport", "52.00", OperationType.DEBIT),
                new SeedOp(52, "Freelance income", "1500.00", OperationType.CREDIT),
                new SeedOp(48, "Salary", "3200.00", OperationType.CREDIT),
                new SeedOp(45, "Rent", "1500.00", OperationType.DEBIT),
                new SeedOp(42, "Groceries", "170.00", OperationType.DEBIT),
                new SeedOp(38, "Utilities", "122.00", OperationType.DEBIT),
                new SeedOp(35, "Restaurant", "88.00", OperationType.DEBIT),
                new SeedOp(32, "Coffee shop", "27.00", OperationType.DEBIT),
                new SeedOp(28, "Insurance", "320.00", OperationType.DEBIT),
                new SeedOp(25, "Transport", "45.00", OperationType.DEBIT),
                new SeedOp(22, "Groceries", "148.00", OperationType.DEBIT),
                new SeedOp(15, "Salary", "3200.00", OperationType.CREDIT),
                new SeedOp(5, "Rent", "1500.00", OperationType.DEBIT)
        );

        BigDecimal balance = BigDecimal.ZERO;
        for (SeedOp op : ops) {
            BigDecimal amount = new BigDecimal(op.amount());
            balance = op.type() == OperationType.CREDIT
                    ? balance.add(amount)
                    : balance.subtract(amount);

            Operation operation = new Operation();
            operation.setAccount(account);
            operation.setType(op.type());
            operation.setAmount(amount);
            operation.setCurrency(Currency.EUR);
            operation.setBalanceAfter(balance);
            operation.setDescription(op.desc());
            operation.setCreatedAt(now.minusDays(op.daysAgo()));
            operationRepository.save(operation);
        }

        account.setBalance(balance);
        accountRepository.save(account);
        log.info("Seeded 50 operations for alice EUR account, final balance: {}", balance);
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
