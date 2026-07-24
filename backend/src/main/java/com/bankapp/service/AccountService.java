package com.bankapp.service;

import com.bankapp.client.DebitEligibilityClient;
import com.bankapp.dto.BankDtos.AccountStatsResponse;
import com.bankapp.dto.BankDtos.AccountSummaryResponse;
import com.bankapp.dto.BankDtos.BalancePoint;
import com.bankapp.dto.BankDtos.CreateAccountRequest;
import com.bankapp.dto.BankDtos.ExchangeRequest;
import com.bankapp.dto.BankDtos.OperationPage;
import com.bankapp.dto.BankDtos.OperationResponse;
import com.bankapp.dto.BankDtos.RecipientCheckResponse;
import com.bankapp.dto.BankDtos.TransferRequest;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long ACCOUNT_MIN = 1_000_000_000L;
    private static final long ACCOUNT_RANGE = 9_000_000_000L;
    private static final int MAX_NUMBER_RETRIES = 10;

    private final AccountRepository accountRepository;
    private final OperationRepository operationRepository;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    private final DebitEligibilityClient debitEligibilityClient;

    // ── Public ID resolution ─────────────────────────────────────────────
    // Translates externally-facing UUIDs to internal DB IDs. Called at the
    // controller boundary, before the resolved Long reaches any
    // @PreAuthorize-guarded method below.

    @Transactional(readOnly = true)
    public Long resolveAccountId(UUID publicId) {
        return accountRepository.findByPublicId(publicId)
                .map(Account::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + publicId));
    }

    @Transactional(readOnly = true)
    public Long resolveUserId(UUID publicId) {
        return userRepository.findByPublicId(publicId)
                .map(User::getId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + publicId));
    }

    @Transactional(readOnly = true)
    public Long resolveOperationId(UUID publicId) {
        return operationRepository.findByPublicId(publicId)
                .map(Operation::getId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + publicId));
    }

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

    @PreAuthorize("@accountSecurity.isSelf(#userId, authentication)")
    @Transactional(readOnly = true)
    public List<AccountSummaryResponse> getAccountsByUser(Long userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    @Transactional(readOnly = true)
    public AccountSummaryResponse getAccountSummary(Long accountId) {
        return toSummary(getAccount(accountId));
    }

    @PreAuthorize("@accountSecurity.isOwner(#sourceAccountId, authentication)")
    public List<OperationResponse> exchange(Long sourceAccountId, ExchangeRequest req) {
        Account source = getAccount(sourceAccountId);
        Account target = accountRepository.findByPublicId(req.targetAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + req.targetAccountId()));

        // Exchange only moves money between the caller's own accounts. Sending to someone
        // else's account - even if you happen to know its UUID - must go through transfer(),
        // which requires proving you know the recipient's username AND account number.
        if (!target.getUser().getId().equals(source.getUser().getId())) {
            throw new ResourceNotFoundException("Account not found: " + req.targetAccountId());
        }
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Cannot exchange between the same account");
        }
        if (source.getBalance().compareTo(req.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Balance: " + source.getBalance() + " " + source.getCurrency());
        }

        BigDecimal rate = exchangeRateService.getRate(source.getCurrency(), target.getCurrency());
        BigDecimal convertedAmount = exchangeRateService.convert(req.amount(), source.getCurrency(), target.getCurrency());
        String desc = String.format("Exchange %.2f %s for %.2f %s (rate: %.2f)",
                req.amount(), source.getCurrency(), convertedAmount, target.getCurrency(), rate);

        return moveFunds(source, target, req.amount(), convertedAmount, rate,
                OperationType.EXCHANGE_OUT, OperationType.EXCHANGE_IN, desc, desc);
    }

    // Read-only pre-check so the frontend can validate a recipient before the user commits to
    // an actual transfer. Uses the exact same matching rule as transfer() itself, so "valid"
    // here always means transfer() will accept it. Deliberately does not reveal the recipient's
    // account currency or any other account detail to the sender.
    @Transactional(readOnly = true)
    public RecipientCheckResponse checkRecipient(String username, String accountNumber) {
        return new RecipientCheckResponse(resolveVerifiedRecipient(username, accountNumber).isPresent());
    }

    @PreAuthorize("@accountSecurity.isOwner(#sourceAccountId, authentication)")
    public List<OperationResponse> transfer(Long sourceAccountId, TransferRequest req) {

        Account source = getAccount(sourceAccountId);

        if (!debitEligibilityClient.isDebitAllowed(source.getUser().getId())) {
            throw new DebitNotAllowedException("Debit not allowed. Please contact support for details");
        }

        // Both the username and the account number must match the SAME account -
        // this is the "recipient verification" step
        Account target = resolveVerifiedRecipient(req.targetUsername(), req.targetAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));
        if (source.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        if (source.getBalance().compareTo(req.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Balance: " + source.getBalance() + " " + source.getCurrency());
        }

        BigDecimal rate = exchangeRateService.getRate(source.getCurrency(), target.getCurrency());
        BigDecimal convertedAmount = exchangeRateService.convert(req.amount(), source.getCurrency(), target.getCurrency());

        String note = req.description() != null && !req.description().isBlank() ? " - " + req.description() : "";
        String outDesc = String.format("Transfer to %s (%s)%s", target.getUser().getUsername(), target.getAccountNumber(), note);
        String inDesc = String.format("Transfer from %s (%s)%s", source.getUser().getUsername(), source.getAccountNumber(), note);

        return moveFunds(source, target, req.amount(), convertedAmount, rate,
                OperationType.TRANSFER_OUT, OperationType.TRANSFER_IN, outDesc, inDesc);
    }

    // Trusted internal transfer for seed/administrative data (currently only DataSeeder). Skips
    // the debit-eligibility check and username/account-number recipient verification that guard
    // the public transfer() endpoint - the caller already has resolved, trusted account ids, not
    // user-supplied ones. Not wired to any controller; must stay that way.
    public List<OperationResponse> transferInternal(Long sourceAccountId, Long targetAccountId,
                                                     BigDecimal amount, String outDescription, String inDescription) {
        Account source = getAccount(sourceAccountId);
        Account target = getAccount(targetAccountId);
        BigDecimal rate = exchangeRateService.getRate(source.getCurrency(), target.getCurrency());
        BigDecimal convertedAmount = exchangeRateService.convert(amount, source.getCurrency(), target.getCurrency());

        return moveFunds(source, target, amount, convertedAmount, rate,
                OperationType.TRANSFER_OUT, OperationType.TRANSFER_IN, outDescription, inDescription);
    }

    private List<OperationResponse> moveFunds(Account source, Account target, BigDecimal amount, BigDecimal convertedAmount,
                                              BigDecimal rate, OperationType outType, OperationType inType,
                                              String outDescription, String inDescription) {
        source.setBalance(source.getBalance().subtract(amount));
        target.setBalance(target.getBalance().add(convertedAmount));
        accountRepository.save(source);
        accountRepository.save(target);

        Operation outOp = buildOperation(source, outType, amount, source.getCurrency(), source.getBalance(), outDescription, rate, target.getId());
        Operation inOp = buildOperation(target, inType, convertedAmount, target.getCurrency(), target.getBalance(), inDescription, rate, source.getId());

        return List.of(
                toOperationResponse(operationRepository.save(outOp)),
                toOperationResponse(operationRepository.save(inOp))
        );
    }

    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    @Transactional(readOnly = true)
    public List<BalancePoint> getBalanceHistory(Long accountId) {
        return operationRepository.findByAccountIdOrderByCreatedAtAsc(accountId).stream()
                .map(op -> new BalancePoint(op.getBalanceAfter(), op.getCreatedAt()))
                .toList();
    }

    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    @Transactional(readOnly = true)
    public OperationPage getTransactionHistoryPaged(Long accountId, Pageable pageable) {
        Page<OperationResponse> page = operationRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(this::toOperationResponse);
        return new OperationPage(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.isLast());
    }

    @PreAuthorize("@accountSecurity.isOwnerOfTransaction(#operationId, authentication)")
    @Transactional(readOnly = true)
    public OperationResponse getTransaction(Long operationId) {
        Operation op = operationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + operationId));
        return toOperationResponse(op);
    }

    @PreAuthorize("@accountSecurity.isOwner(#accountId, authentication)")
    @Transactional(readOnly = true)
    public AccountStatsResponse getAccountStats(Long accountId) {
        BigDecimal totalIn = operationRepository.sumAmountByAccountIdAndTypes(
                accountId, List.of(OperationType.EXCHANGE_IN, OperationType.TRANSFER_IN));
        BigDecimal totalOut = operationRepository.sumAmountByAccountIdAndTypes(
                accountId, List.of(OperationType.EXCHANGE_OUT, OperationType.TRANSFER_OUT));
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

    // ── Helpers ───────────────────────────────────────────────────────────

    private Account getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    private Optional<Account> resolveVerifiedRecipient(String username, String accountNumber) {
        return userRepository.findByUsername(username)
                .flatMap(user -> accountRepository.findByAccountNumber(accountNumber)
                        .filter(acc -> acc.getUser().getId().equals(user.getId())));
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
        for (int i = 0; i < MAX_NUMBER_RETRIES; i++) {
            String number = String.valueOf(ACCOUNT_MIN + RANDOM.nextLong(ACCOUNT_RANGE));
            if (!accountRepository.existsByAccountNumber(number)) {
                return number;
            }
        }
        throw new IllegalStateException("Failed to generate a unique account number after " + MAX_NUMBER_RETRIES + " attempts");
    }

    private AccountSummaryResponse toSummary(Account a) {
        return new AccountSummaryResponse(a.getPublicId(), a.getAccountNumber(), a.getCurrency(),
                a.getBalance(), a.getUser().getPublicId(), a.getUser().getUsername());
    }

    private OperationResponse toOperationResponse(Operation op) {
        Account related = op.getRelatedAccountId() == null ? null
                : accountRepository.findById(op.getRelatedAccountId()).orElse(null);
        return new OperationResponse(op.getPublicId(), op.getAccount().getPublicId(), op.getAccount().getAccountNumber(),
                op.getType(), op.getAmount(), op.getCurrency(), op.getBalanceAfter(),
                op.getDescription(), op.getCreatedAt(), op.getExchangeRate(),
                related != null ? related.getPublicId() : null,
                related != null ? related.getAccountNumber() : null);
    }
}
