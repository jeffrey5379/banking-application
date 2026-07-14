package com.bankapp.security;

import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.Account;
import com.bankapp.model.Currency;
import com.bankapp.model.Operation;
import com.bankapp.model.OperationType;
import com.bankapp.model.User;
import com.bankapp.repository.AccountRepository;
import com.bankapp.repository.OperationRepository;
import com.bankapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountSecurityTest {

    @Mock private AccountRepository accountRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private UserRepository userRepository;
    @Mock private Authentication authentication;

    @InjectMocks
    private AccountSecurity accountSecurity;

    private User alice;
    private User bob;
    private Account aliceAccount;

    @BeforeEach
    void setUp() {
        alice = new User();
        alice.setId(1L);
        alice.setUsername("alice");

        bob = new User();
        bob.setId(2L);
        bob.setUsername("bob");

        aliceAccount = new Account();
        aliceAccount.setId(10L);
        aliceAccount.setAccountNumber("ACC-AAAAAAAA");
        aliceAccount.setCurrency(Currency.EUR);
        aliceAccount.setBalance(BigDecimal.ZERO);
        aliceAccount.setUser(alice);

        when(authentication.getName()).thenReturn("alice");
    }

    // ── isOwner ───────────────────────────────────────────────────────────

    @Test
    void isOwner_accountBelongsToCurrentUser_returnsTrue() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(aliceAccount));
        assertThat(accountSecurity.isOwner(10L, authentication)).isTrue();
    }

    @Test
    void isOwner_accountBelongsToOtherUser_returnsFalse() {
        Account bobAccount = buildAccount(20L, bob);
        when(accountRepository.findById(20L)).thenReturn(Optional.of(bobAccount));
        assertThat(accountSecurity.isOwner(20L, authentication)).isFalse();
    }

    @Test
    void isOwner_accountNotFound_throwsResourceNotFoundException() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accountSecurity.isOwner(99L, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── isOwnerOfTransaction ──────────────────────────────────────────────

    @Test
    void isOwnerOfTransaction_transactionBelongsToCurrentUser_returnsTrue() {
        Operation op = buildOperation(aliceAccount);
        when(operationRepository.findById(1L)).thenReturn(Optional.of(op));
        assertThat(accountSecurity.isOwnerOfTransaction(1L, authentication)).isTrue();
    }

    @Test
    void isOwnerOfTransaction_transactionBelongsToOtherUser_returnsFalse() {
        Account bobAccount = buildAccount(20L, bob);
        Operation op = buildOperation(bobAccount);
        when(operationRepository.findById(1L)).thenReturn(Optional.of(op));
        assertThat(accountSecurity.isOwnerOfTransaction(1L, authentication)).isFalse();
    }

    @Test
    void isOwnerOfTransaction_notFound_throwsResourceNotFoundException() {
        when(operationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accountSecurity.isOwnerOfTransaction(99L, authentication))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── isSelf ────────────────────────────────────────────────────────────

    @Test
    void isSelf_matchingUserId_returnsTrue() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        assertThat(accountSecurity.isSelf(1L, authentication)).isTrue();
    }

    @Test
    void isSelf_differentUserId_returnsFalse() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        assertThat(accountSecurity.isSelf(99L, authentication)).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Account buildAccount(Long id, User user) {
        Account a = new Account();
        a.setId(id);
        a.setAccountNumber("ACC-" + id);
        a.setCurrency(Currency.EUR);
        a.setBalance(BigDecimal.ZERO);
        a.setUser(user);
        return a;
    }

    private Operation buildOperation(Account account) {
        Operation op = new Operation();
        op.setAccount(account);
        op.setType(OperationType.CREDIT);
        op.setAmount(new BigDecimal("100.00"));
        op.setCurrency(account.getCurrency());
        op.setBalanceAfter(new BigDecimal("100.00"));
        return op;
    }
}
