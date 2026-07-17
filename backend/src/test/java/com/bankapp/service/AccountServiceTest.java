package com.bankapp.service;

import com.bankapp.client.DebitEligibilityClient;
import com.bankapp.dto.BankDtos.*;
import com.bankapp.exception.DebitNotAllowedException;
import com.bankapp.exception.InsufficientFundsException;
import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.*;
import com.bankapp.repository.AccountRepository;
import com.bankapp.repository.OperationRepository;
import com.bankapp.repository.UserRepository;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ExchangeRateService exchangeRateService;
    @Mock private DebitEligibilityClient debitEligibilityClient;

    @InjectMocks
    private AccountService accountService;

    private User alice;
    private User bob;
    private Account eurAccount;
    private Account usdAccount;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");
        alice.setEmail("alice@example.com");
        alice.setPassword("$2a$10$hash");

        bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");
        bob.setEmail("bob@example.com");
        bob.setPassword("$2a$10$hash");

        eurAccount = new Account();
        eurAccount.setId(10L);
        eurAccount.setAccountNumber("ACC-AAAAAAAA");
        eurAccount.setCurrency(Currency.EUR);
        eurAccount.setBalance(new BigDecimal("1000.00"));
        eurAccount.setUser(alice);

        usdAccount = new Account();
        usdAccount.setId(20L);
        usdAccount.setAccountNumber("ACC-BBBBBBBB");
        usdAccount.setCurrency(Currency.USD);
        usdAccount.setBalance(new BigDecimal("500.00"));
        usdAccount.setUser(alice);

        setCurrentUser("alice");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(debitEligibilityClient.isDebitAllowed(1L)).thenReturn(true);
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
        assertThat(result.userId()).isEqualTo(alice.getPublicId());
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
    void createAccount_authenticatedUserNotFound_throwsResourceNotFoundException() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.createAccount(new CreateAccountRequest(Currency.EUR)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getAccountsByUser ─────────────────────────────────────────────────

    @Test
    void getAccountsByUser_validUser_returnsAccounts() {
        when(accountRepository.findByUserId(1L)).thenReturn(List.of(eurAccount, usdAccount));

        List<AccountSummaryResponse> result = accountService.getAccountsByUser(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AccountSummaryResponse::currency)
                .containsExactlyInAnyOrder(Currency.EUR, Currency.USD);
    }


    // ── credit ────────────────────────────────────────────────────────────

    @Test
    void credit_validAmount_increasesBalanceAndSavesTransaction() {
        Operation savedOp = buildOp(eurAccount, OperationType.CREDIT, new BigDecimal("200.00"),
                new BigDecimal("1200.00"));

        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(accountRepository.save(eurAccount)).thenReturn(eurAccount);
        when(operationRepository.save(any(Operation.class))).thenReturn(savedOp);

        OperationResponse result = accountService.credit(10L, new MoneyRequest(new BigDecimal("200.00"), "Salary"));

        assertThat(eurAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(result.type()).isEqualTo(OperationType.CREDIT);
        assertThat(result.amount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    void credit_nullDescription_usesDefaultDescription() {
        Operation savedOp = buildOp(eurAccount, OperationType.CREDIT, new BigDecimal("100.00"),
                new BigDecimal("1100.00"));
        savedOp.setDescription("Credit");

        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(accountRepository.save(any())).thenReturn(eurAccount);
        when(operationRepository.save(any(Operation.class))).thenReturn(savedOp);

        OperationResponse result = accountService.credit(10L, new MoneyRequest(new BigDecimal("100.00"), null));

        assertThat(result.description()).isEqualTo("Credit");
    }


    // ── debit ─────────────────────────────────────────────────────────────

    @Test
    void debit_sufficientFunds_decreasesBalanceAndSavesTransaction() {
        Operation savedOp = buildOp(eurAccount, OperationType.DEBIT, new BigDecimal("300.00"),
                new BigDecimal("700.00"));

        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(accountRepository.save(eurAccount)).thenReturn(eurAccount);
        when(operationRepository.save(any(Operation.class))).thenReturn(savedOp);

        OperationResponse result = accountService.debit(10L, new MoneyRequest(new BigDecimal("300.00"), "Rent"));

        assertThat(eurAccount.getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));
        assertThat(result.type()).isEqualTo(OperationType.DEBIT);
    }

    @Test
    void debit_insufficientFunds_throwsInsufficientFundsException() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));

        assertThatThrownBy(() -> accountService.debit(10L, new MoneyRequest(new BigDecimal("9999.00"), null)))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        verify(operationRepository, never()).save(any());
    }

    @Test
    void debit_exactBalance_succeeds() {
        Operation savedOp = buildOp(eurAccount, OperationType.DEBIT, new BigDecimal("1000.00"),
                BigDecimal.ZERO);

        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));
        when(accountRepository.save(any())).thenReturn(eurAccount);
        when(operationRepository.save(any(Operation.class))).thenReturn(savedOp);

        assertThatCode(() -> accountService.debit(10L, new MoneyRequest(new BigDecimal("1000.00"), null)))
                .doesNotThrowAnyException();
    }

    @Test
    void debit_eligibilityDenied_throwsDebitNotAllowedException() {
        when(debitEligibilityClient.isDebitAllowed(1L)).thenReturn(false);
        when(accountRepository.findById(10L)).thenReturn(Optional.of(eurAccount));

        assertThatThrownBy(() -> accountService.debit(10L, new MoneyRequest(new BigDecimal("100.00"), null)))
                .isInstanceOf(DebitNotAllowedException.class)
                .hasMessageContaining("Debit not allowed");

        verify(operationRepository, never()).save(any());
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


    // ── getTransactionHistory ─────────────────────────────────────────────

    // ── getTransaction ────────────────────────────────────────────────────

    @Test
    void getTransaction_existingId_returnsTransaction() {
        Operation op = buildOp(eurAccount, OperationType.CREDIT, new BigDecimal("100.00"),
                new BigDecimal("1100.00"));
        op.setId(5L);

        when(operationRepository.findById(5L)).thenReturn(Optional.of(op));

        OperationResponse result = accountService.getTransaction(5L);

        assertThat(result.id()).isEqualTo(op.getPublicId());
        assertThat(result.type()).isEqualTo(OperationType.CREDIT);
    }

    @Test
    void getTransaction_notFound_throwsResourceNotFoundException() {
        when(operationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getTransaction(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private void setCurrentUser(String username) {
        org.springframework.security.core.userdetails.UserDetails ud =
                org.springframework.security.core.userdetails.User.builder()
                        .username(username).password("").roles("USER").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities()));
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
