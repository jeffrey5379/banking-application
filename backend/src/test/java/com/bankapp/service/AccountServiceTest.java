package com.bankapp.service;

import com.bankapp.client.DebitEligibilityClient;
import com.bankapp.client.KycStatusClient;
import com.bankapp.dto.BankDtos.*;
import com.bankapp.exception.DebitNotAllowedException;
import com.bankapp.exception.InsufficientFundsException;
import com.bankapp.exception.KycNotVerifiedException;
import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.*;
import com.bankapp.repository.AccountRepository;
import com.bankapp.repository.OperationRepository;
import com.bankapp.security.JwtPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private DebitEligibilityClient debitEligibilityClient;
    @Mock private KycStatusClient kycStatusClient;

    @InjectMocks
    private AccountService accountService;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID = UUID.randomUUID();

    private Account eurAccount;
    private Account usdAccount;
    private Account bobAccount;

    @BeforeEach
    void setUp() {
        eurAccount = new Account();
        eurAccount.setId(10L);
        eurAccount.setAccountNumber("ACC-AAAAAAAA");
        eurAccount.setCurrency(Currency.EUR);
        eurAccount.setBalance(new BigDecimal("1000.00"));
        eurAccount.setOwnerId(ALICE_ID);
        eurAccount.setOwnerUsername("alice");

        usdAccount = new Account();
        usdAccount.setId(20L);
        usdAccount.setAccountNumber("ACC-BBBBBBBB");
        usdAccount.setCurrency(Currency.USD);
        usdAccount.setBalance(new BigDecimal("500.00"));
        usdAccount.setOwnerId(ALICE_ID);
        usdAccount.setOwnerUsername("alice");

        bobAccount = new Account();
        bobAccount.setId(30L);
        bobAccount.setAccountNumber("ACC-CCCCCCCC");
        bobAccount.setCurrency(Currency.EUR);
        bobAccount.setBalance(new BigDecimal("200.00"));
        bobAccount.setOwnerId(BOB_ID);
        bobAccount.setOwnerUsername("bob");

        setCurrentUser("alice", ALICE_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── createAccount ─────────────────────────────────────────────────────

    @Test
    void createAccount_validRequest_savesAndReturns() {
        when(accountRepository.save(any(Account.class))).thenReturn(eurAccount);

        AccountSummaryResponse result = accountService.createAccount(new CreateAccountRequest(Currency.EUR));

        assertThat(result.currency()).isEqualTo(Currency.EUR);
        assertThat(result.userId()).isEqualTo(ALICE_ID);
        assertThat(result.username()).isEqualTo("alice");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccount_accountNumberCollision_retriesAndSucceeds() {
        when(accountRepository.existsByAccountNumber(any())).thenReturn(true, false);
        when(accountRepository.save(any(Account.class))).thenReturn(eurAccount);

        AccountSummaryResponse result = accountService.createAccount(new CreateAccountRequest(Currency.EUR));

        assertThat(result.currency()).isEqualTo(Currency.EUR);
        verify(accountRepository, times(2)).existsByAccountNumber(any());
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void createAccount_allRetriesExhausted_throwsIllegalStateException() {
        when(accountRepository.existsByAccountNumber(any())).thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(new CreateAccountRequest(Currency.EUR)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unique account number");

        verify(accountRepository, times(10)).existsByAccountNumber(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void createAccount_kycNotVerified_propagatesExceptionAndSavesNothing() {
        doThrow(new KycNotVerifiedException("Identity verification required before this action is allowed"))
                .when(kycStatusClient).requireVerified(ALICE_ID);

        assertThatThrownBy(() -> accountService.createAccount(new CreateAccountRequest(Currency.EUR)))
                .isInstanceOf(KycNotVerifiedException.class);

        verify(accountRepository, never()).save(any());
    }

    // ── getAccountsByUser ─────────────────────────────────────────────────

    @Test
    void getAccountsByUser_validUser_returnsAccounts() {
        when(accountRepository.findByOwnerId(ALICE_ID)).thenReturn(List.of(eurAccount, usdAccount));

        List<AccountSummaryResponse> result = accountService.getAccountsByUser(ALICE_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AccountSummaryResponse::currency)
                .containsExactlyInAnyOrder(Currency.EUR, Currency.USD);
    }


    // ── transferInternal ─────────────────────────────────────────────────

    @Test
    void transferInternal_validRequest_movesFundsAndCreatesPairedTransactions() {
        BigDecimal rate = BigDecimal.ONE;
        BigDecimal converted = new BigDecimal("100.00");

        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(accountRepository.findById(30L)).thenReturn(Optional.of(bobAccount));
        when(exchangeRateService.getRate(Currency.EUR, Currency.EUR)).thenReturn(rate);
        when(exchangeRateService.convert(new BigDecimal("100.00"), Currency.EUR, Currency.EUR)).thenReturn(converted);
        when(accountRepository.save(any())).thenReturn(eurAccount, bobAccount);

        Operation outOp = buildOp(eurAccount, OperationType.TRANSFER_OUT, new BigDecimal("100.00"), new BigDecimal("900.00"));
        Operation inOp = buildOp(bobAccount, OperationType.TRANSFER_IN, converted, new BigDecimal("300.00"));
        when(operationRepository.save(any(Operation.class))).thenReturn(outOp, inOp);

        List<OperationResponse> result = accountService.transferInternal(
                10L, 30L, new BigDecimal("100.00"), "Seed out", "Seed in");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(OperationType.TRANSFER_OUT);
        assertThat(result.get(1).type()).isEqualTo(OperationType.TRANSFER_IN);
        assertThat(eurAccount.getBalance()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(bobAccount.getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void transferInternal_doesNotCallDebitEligibilityOrKycCheck() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(accountRepository.findById(30L)).thenReturn(Optional.of(bobAccount));
        when(exchangeRateService.getRate(Currency.EUR, Currency.EUR)).thenReturn(BigDecimal.ONE);
        when(exchangeRateService.convert(any(), eq(Currency.EUR), eq(Currency.EUR))).thenReturn(new BigDecimal("50.00"));
        when(accountRepository.save(any())).thenReturn(eurAccount, bobAccount);
        when(operationRepository.save(any(Operation.class)))
                .thenReturn(buildOp(eurAccount, OperationType.TRANSFER_OUT, new BigDecimal("50.00"), new BigDecimal("950.00")))
                .thenReturn(buildOp(bobAccount, OperationType.TRANSFER_IN, new BigDecimal("50.00"), new BigDecimal("250.00")));

        accountService.transferInternal(10L, 30L, new BigDecimal("50.00"), "Seed out", "Seed in");

        verify(debitEligibilityClient, never()).isDebitAllowed(any());
        verify(kycStatusClient, never()).requireVerified(any());
    }

    // ── exchange ──────────────────────────────────────────────────────────

    @Test
    void exchange_validRequest_convertsAndCreatesTransactions() {
        BigDecimal rate = new BigDecimal("1.086957");
        BigDecimal converted = new BigDecimal("108.6957");

        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(accountRepository.findByPublicId(usdAccount.getPublicId())).thenReturn(Optional.of(usdAccount));
        when(exchangeRateService.getRate(Currency.EUR, Currency.USD)).thenReturn(rate);
        when(exchangeRateService.convert(new BigDecimal("100.00"), Currency.EUR, Currency.USD)).thenReturn(converted);
        when(accountRepository.save(any())).thenReturn(eurAccount, usdAccount);

        Operation outOp = buildOp(eurAccount, OperationType.EXCHANGE_OUT, new BigDecimal("100.00"),
                new BigDecimal("900.00"));
        Operation inOp = buildOp(usdAccount, OperationType.EXCHANGE_IN, converted,
                new BigDecimal("608.6957"));
        when(operationRepository.save(any(Operation.class))).thenReturn(outOp, inOp);

        List<OperationResponse> result = accountService.exchange(10L,
                new ExchangeRequest(new BigDecimal("100.00"), usdAccount.getPublicId()));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(OperationType.EXCHANGE_OUT);
        assertThat(result.get(1).type()).isEqualTo(OperationType.EXCHANGE_IN);

        assertThat(eurAccount.getBalance()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(usdAccount.getBalance()).isEqualByComparingTo(new BigDecimal("608.6957"));
    }

    @Test
    void exchange_sameAccount_throwsIllegalArgumentException() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(accountRepository.findByPublicId(eurAccount.getPublicId())).thenReturn(Optional.of(eurAccount));

        assertThatThrownBy(() -> accountService.exchange(10L,
                new ExchangeRequest(new BigDecimal("100.00"), eurAccount.getPublicId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same account");
    }

    @Test
    void exchange_insufficientFunds_throwsInsufficientFundsException() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(accountRepository.findByPublicId(usdAccount.getPublicId())).thenReturn(Optional.of(usdAccount));

        assertThatThrownBy(() -> accountService.exchange(10L,
                new ExchangeRequest(new BigDecimal("5000.00"), usdAccount.getPublicId())))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void exchange_targetOwnedByAnotherUser_throwsResourceNotFoundException() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(accountRepository.findByPublicId(bobAccount.getPublicId())).thenReturn(Optional.of(bobAccount));

        assertThatThrownBy(() -> accountService.exchange(10L,
                new ExchangeRequest(new BigDecimal("100.00"), bobAccount.getPublicId())))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(accountRepository, never()).save(any());
        verify(operationRepository, never()).save(any());
    }

    // ── transfer ──────────────────────────────────────────────────────────

    @Test
    void transfer_validRequest_convertsAndCreatesTransferTransactions() {
        BigDecimal rate = BigDecimal.ONE;
        BigDecimal converted = new BigDecimal("100.00");

        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(debitEligibilityClient.isDebitAllowed(10L)).thenReturn(true);
        when(accountRepository.findByAccountNumber("ACC-CCCCCCCC")).thenReturn(Optional.of(bobAccount));
        when(exchangeRateService.getRate(Currency.EUR, Currency.EUR)).thenReturn(rate);
        when(exchangeRateService.convert(new BigDecimal("100.00"), Currency.EUR, Currency.EUR)).thenReturn(converted);
        when(accountRepository.save(any())).thenReturn(eurAccount, bobAccount);

        Operation outOp = buildOp(eurAccount, OperationType.TRANSFER_OUT, new BigDecimal("100.00"),
                new BigDecimal("900.00"));
        Operation inOp = buildOp(bobAccount, OperationType.TRANSFER_IN, converted,
                new BigDecimal("300.00"));
        when(operationRepository.save(any(Operation.class))).thenReturn(outOp, inOp);

        List<OperationResponse> result = accountService.transfer(10L,
                new TransferRequest(new BigDecimal("100.00"), "bob", "ACC-CCCCCCCC", "Dinner"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).type()).isEqualTo(OperationType.TRANSFER_OUT);
        assertThat(result.get(1).type()).isEqualTo(OperationType.TRANSFER_IN);
        assertThat(eurAccount.getBalance()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(bobAccount.getBalance()).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    void transfer_unknownUsername_throwsResourceNotFoundException() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(debitEligibilityClient.isDebitAllowed(10L)).thenReturn(true);
        when(accountRepository.findByAccountNumber("ACC-CCCCCCCC")).thenReturn(Optional.of(bobAccount));

        assertThatThrownBy(() -> accountService.transfer(10L,
                new TransferRequest(new BigDecimal("100.00"), "nobody", "ACC-CCCCCCCC", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Recipient not found");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void transfer_unknownAccountNumber_throwsResourceNotFoundException() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(debitEligibilityClient.isDebitAllowed(10L)).thenReturn(true);
        when(accountRepository.findByAccountNumber("BOGUS")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.transfer(10L,
                new TransferRequest(new BigDecimal("100.00"), "bob", "BOGUS", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Recipient not found");
    }

    @Test
    void transfer_usernameAndAccountNumberDontMatchTheSameAccount_throwsResourceNotFoundException() {
        // "bob" is real and "ACC-AAAAAAAA" is real, but ACC-AAAAAAAA belongs to alice, not bob.
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(debitEligibilityClient.isDebitAllowed(10L)).thenReturn(true);
        when(accountRepository.findByAccountNumber("ACC-AAAAAAAA")).thenReturn(Optional.of(eurAccount));

        assertThatThrownBy(() -> accountService.transfer(10L,
                new TransferRequest(new BigDecimal("100.00"), "bob", "ACC-AAAAAAAA", null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Recipient not found");

        verify(accountRepository, never()).save(any());
    }

    @Test
    void transfer_sameAccount_throwsIllegalArgumentException() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(debitEligibilityClient.isDebitAllowed(10L)).thenReturn(true);
        when(accountRepository.findByAccountNumber("ACC-AAAAAAAA")).thenReturn(Optional.of(eurAccount));

        assertThatThrownBy(() -> accountService.transfer(10L,
                new TransferRequest(new BigDecimal("100.00"), "alice", "ACC-AAAAAAAA", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same account");
    }

    @Test
    void transfer_insufficientFunds_throwsInsufficientFundsException() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(debitEligibilityClient.isDebitAllowed(10L)).thenReturn(true);
        when(accountRepository.findByAccountNumber("ACC-CCCCCCCC")).thenReturn(Optional.of(bobAccount));

        assertThatThrownBy(() -> accountService.transfer(10L,
                new TransferRequest(new BigDecimal("5000.00"), "bob", "ACC-CCCCCCCC", null)))
                .isInstanceOf(InsufficientFundsException.class);

        verify(operationRepository, never()).save(any());
    }

    @Test
    void transfer_eligibilityDenied_throwsDebitNotAllowedException() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(debitEligibilityClient.isDebitAllowed(10L)).thenReturn(false);

        assertThatThrownBy(() -> accountService.transfer(10L,
                new TransferRequest(new BigDecimal("100.00"), "bob", "ACC-CCCCCCCC", null)))
                .isInstanceOf(DebitNotAllowedException.class)
                .hasMessageContaining("Debit not allowed");

        verify(accountRepository, never()).save(any());
        verify(operationRepository, never()).save(any());
    }

    @Test
    void transfer_kycNotVerified_propagatesExceptionBeforeEligibilityCheck() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        doThrow(new KycNotVerifiedException("Identity verification required before this action is allowed"))
                .when(kycStatusClient).requireVerified(ALICE_ID);

        assertThatThrownBy(() -> accountService.transfer(10L,
                new TransferRequest(new BigDecimal("100.00"), "bob", "ACC-CCCCCCCC", null)))
                .isInstanceOf(KycNotVerifiedException.class);

        verify(debitEligibilityClient, never()).isDebitAllowed(any());
        verify(accountRepository, never()).save(any());
    }

    // ── checkRecipient ───────────────────────────────────────────────────────

    @Test
    void checkRecipient_matchingUsernameAndAccountNumber_returnsValid() {
        when(accountRepository.findByAccountNumber("ACC-CCCCCCCC")).thenReturn(Optional.of(bobAccount));

        RecipientCheckResponse result = accountService.checkRecipient("bob", "ACC-CCCCCCCC");

        assertThat(result.valid()).isTrue();
    }

    @Test
    void checkRecipient_unknownAccountNumber_returnsInvalid() {
        when(accountRepository.findByAccountNumber("BOGUS")).thenReturn(Optional.empty());

        RecipientCheckResponse result = accountService.checkRecipient("bob", "BOGUS");

        assertThat(result.valid()).isFalse();
    }

    @Test
    void checkRecipient_usernameAndAccountNumberDontMatchTheSameAccount_returnsInvalid() {
        when(accountRepository.findByAccountNumber("ACC-AAAAAAAA")).thenReturn(Optional.of(eurAccount));

        RecipientCheckResponse result = accountService.checkRecipient("bob", "ACC-AAAAAAAA");

        assertThat(result.valid()).isFalse();
    }

    @Test
    void checkRecipient_doesNotMoveAnyMoney() {
        when(accountRepository.findByAccountNumber("ACC-CCCCCCCC")).thenReturn(Optional.of(bobAccount));

        accountService.checkRecipient("bob", "ACC-CCCCCCCC");

        verify(accountRepository, never()).save(any());
        verify(operationRepository, never()).save(any());
    }

    // ── getTransaction ────────────────────────────────────────────────────

    @Test
    void getTransaction_existingId_returnsTransaction() {
        Operation op = buildOp(eurAccount, OperationType.TRANSFER_IN, new BigDecimal("100.00"),
                new BigDecimal("1100.00"));
        op.setId(5L);

        when(operationRepository.findById(5L)).thenReturn(Optional.of(op));

        OperationResponse result = accountService.getTransaction(5L);

        assertThat(result.id()).isEqualTo(op.getPublicId());
        assertThat(result.type()).isEqualTo(OperationType.TRANSFER_IN);
    }

    @Test
    void getTransaction_notFound_throwsResourceNotFoundException() {
        when(operationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getTransaction(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private void setCurrentUser(String username, UUID userId) {
        JwtPrincipal principal = new JwtPrincipal(username, userId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Operation buildOp(Account account, OperationType type, BigDecimal amount, BigDecimal balanceAfter) {
        Operation op = new Operation();
        op.setAccount(account);
        op.setType(type);
        op.setAmount(amount);
        op.setCurrency(account.getCurrency());
        op.setBalanceAfter(balanceAfter);
        op.setCreatedAt(LocalDateTime.now());
        return op;
    }

}
