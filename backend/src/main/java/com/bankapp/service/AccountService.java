package com.bankapp.service;

import com.bankapp.client.DebitEligibilityClient;
import com.bankapp.dto.BankDtos.AccountStatsResponse;
import com.bankapp.dto.BankDtos.AccountSummaryResponse;
import com.bankapp.dto.BankDtos.BalancePoint;
import com.bankapp.dto.BankDtos.CreateAccountRequest;
import com.bankapp.dto.BankDtos.ExchangeRequest;
import com.bankapp.dto.BankDtos.MoneyRequest;
import com.bankapp.dto.BankDtos.OperationPage;
import com.bankapp.dto.BankDtos.OperationResponse;
import com.bankapp.exception.DebitNotAllowedException;
import com.bankapp.exception.InsufficientFundsException;
import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.Account;
import com.bankapp.model.Currency;
import com.bankapp.model.Operation;
import com.bankapp.model.OperationType;
import com.bankapp.model.User;
import com.bankapp.repository.AccountRepository;
import com.bankapp.repository.OperationRepository;
import com.bankapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final OperationRepository operationRepository;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final DebitEligibilityClient debitEligibilityClient;

    // ── Account management ────────────────────────────────────────────────

    public AccountSummaryResponse createAccount(CreateAccountRequest req) {
        User user = currentUser();
        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setCurrency(req.currency());
        account.setBalance(BigDecimal.ZERO);
        account.setUser(user);
        return toSummary(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountSummaryResponse> getAccountsByUser(Long userId) {
        User user = currentUser();
        if (!user.getId().equals(userId)) {
            throw new AccessDeniedException("Access denied");
        }
        return accountRepository.findByUserId(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountSummaryResponse getAccountSummary(Long accountId) {
        Account account = getAccount(accountId);
        requireOwner(account);
        return toSummary(account);
    }

    public OperationResponse credit(Long accountId, MoneyRequest req) {
        Account account = getAccount(accountId);
        requireOwner(account);
        account.setBalance(account.getBalance().add(req.amount()));
        accountRepository.save(account);

        Operation op = buildOperation(account, OperationType.CREDIT,
                req.amount(), account.getCurrency(), account.getBalance(),
                req.description() != null ? req.description() : "Credit", null, null);

        return toOperationResponse(operationRepository.save(op));
    }

    public OperationResponse debit(Long accountId, MoneyRequest req) {
        Account account = getAccount(accountId);
        requireOwner(account);
        if (!debitEligibilityClient.isDebitAllowed(account.getUser().getId())) {
            throw new DebitNotAllowedException("Debit not allowed. Please contact support for details");
        }
        if (account.getBalance().compareTo(req.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Balance: " + account.getBalance() + " " + account.getCurrency());
        }
        account.setBalance(account.getBalance().subtract(req.amount()));
        accountRepository.save(account);

        Operation op = buildOperation(account, OperationType.DEBIT,
                req.amount(), account.getCurrency(), account.getBalance(),
                req.description() != null ? req.description() : "Debit", null, null);
        return toOperationResponse(operationRepository.save(op));
    }

    public List<OperationResponse> exchange(Long sourceAccountId, ExchangeRequest req) {
        Account source = getAccount(sourceAccountId);
        Account target = getAccount(req.targetAccountId());

        requireOwner(source);

        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Cannot exchange between the same account");
        }
        if (source.getBalance().compareTo(req.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Balance: " + source.getBalance() + " " + source.getCurrency());
        }

        BigDecimal rate = exchangeRateService.getRate(source.getCurrency(), target.getCurrency());
        BigDecimal convertedAmount = exchangeRateService.convert(req.amount(), source.getCurrency(), target.getCurrency());

        source.setBalance(source.getBalance().subtract(req.amount()));
        target.setBalance(target.getBalance().add(convertedAmount));
        accountRepository.save(source);
        accountRepository.save(target);

        String desc = String.format("Exchange %.2f %s for %.2f %s (rate: %.2f)",
                req.amount(), source.getCurrency(), convertedAmount, target.getCurrency(), rate);

        Operation outOp = buildOperation(source, OperationType.EXCHANGE_OUT,
                req.amount(), source.getCurrency(), source.getBalance(), desc, rate, target.getId());
        Operation inOp = buildOperation(target, OperationType.EXCHANGE_IN,
                convertedAmount, target.getCurrency(), target.getBalance(), desc, rate, source.getId());

        return List.of(
                toOperationResponse(operationRepository.save(outOp)),
                toOperationResponse(operationRepository.save(inOp))
        );
    }

    @Transactional(readOnly = true)
    public List<BalancePoint> getBalanceHistory(Long accountId) {
        Account account = getAccount(accountId);
        requireOwner(account);
        return operationRepository.findByAccountIdOrderByCreatedAtAsc(accountId).stream()
                .map(op -> new BalancePoint(op.getBalanceAfter(), op.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OperationPage getTransactionHistoryPaged(Long accountId, Pageable pageable) {
        Account account = getAccount(accountId);
        requireOwner(account);
        Page<OperationResponse> page = operationRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(this::toOperationResponse);
        return new OperationPage(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.isLast());
    }

    @Transactional(readOnly = true)
    public OperationResponse getTransaction(Long operationId) {
        Operation op = operationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + operationId));
        requireOwner(op.getAccount());
        return toOperationResponse(op);
    }

    @Transactional(readOnly = true)
    public AccountStatsResponse getAccountStats(Long accountId) {
        Account account = getAccount(accountId);
        requireOwner(account);
        BigDecimal totalIn = operationRepository.sumAmountByAccountIdAndTypes(
                accountId, List.of(OperationType.CREDIT, OperationType.EXCHANGE_IN));
        BigDecimal totalOut = operationRepository.sumAmountByAccountIdAndTypes(
                accountId, List.of(OperationType.DEBIT, OperationType.EXCHANGE_OUT));
        return new AccountStatsResponse(totalIn, totalOut);
    }

    // ── Auth helpers ──────────────────────────────────────────────────────

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private User currentUser() {
        String username = currentUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
    }

    private void requireOwner(Account account) {
        if (!account.getUser().getUsername().equals(currentUsername())) {
            throw new AccessDeniedException("Access denied to account " + account.getId());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Account getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    private Operation buildOperation(Account account, OperationType type, BigDecimal amount,
                                     Currency currency, BigDecimal balanceAfter, String description,
                                     BigDecimal rate, Long relatedAccountId) {
        Operation op = new Operation();
        op.setAccount(account);
        op.setType(type);
        op.setAmount(amount);
        op.setCurrency(currency);
        op.setBalanceAfter(balanceAfter);
        op.setDescription(description);
        op.setCreatedAt(LocalDateTime.now());
        op.setExchangeRate(rate);
        op.setRelatedAccountId(relatedAccountId);
        return op;
    }

    private String generateAccountNumber() {
        return String.format("%010d", new SecureRandom().nextInt(1_000_000));
    }

    private AccountSummaryResponse toSummary(Account a) {
        return new AccountSummaryResponse(a.getId(), a.getAccountNumber(), a.getCurrency(),
                a.getBalance(), a.getUser().getId(), a.getUser().getUsername());
    }

    private OperationResponse toOperationResponse(Operation op) {
        return new OperationResponse(op.getId(), op.getAccount().getId(), op.getAccount().getAccountNumber(),
                op.getType(), op.getAmount(), op.getCurrency(), op.getBalanceAfter(),
                op.getDescription(), op.getCreatedAt(), op.getExchangeRate(), op.getRelatedAccountId());
    }
}
