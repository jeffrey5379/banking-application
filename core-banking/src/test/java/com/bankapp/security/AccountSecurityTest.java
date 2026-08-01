package com.bankapp.security;

import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.Account;
import com.bankapp.model.Currency;
import com.bankapp.model.Operation;
import com.bankapp.model.OperationType;
import com.bankapp.repository.AccountRepository;
import com.bankapp.repository.OperationRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountSecurityTest {

    @Mock private AccountRepository accountRepository;
    @Mock private OperationRepository operationRepository;
    @Mock private Authentication authentication;

    @InjectMocks
    private AccountSecurity accountSecurity;

    private static final UUID ALICE_ID = UUID.randomUUID();
    private static final UUID BOB_ID = UUID.randomUUID();

    private Account aliceAccount;

    @BeforeEach
    void setUp() {
        aliceAccount = new Account();
        aliceAccount.setId(10L);
        aliceAccount.setAccountNumber("ACC-AAAAAAAA");
        aliceAccount.setCurrency(Currency.EUR);
        aliceAccount.setBalance(BigDecimal.ZERO);
        aliceAccount.setOwnerId(ALICE_ID);
        aliceAccount.setOwnerUsername("alice");

        when(authentication.getPrincipal()).thenReturn(new JwtPrincipal("alice", ALICE_ID));
    }

    // ── isOwner ───────────────────────────────────────────────────────────

    @Test
    void isOwner_accountBelongsToCurrentUser_returnsTrue() {
        when(accountRepository.findById(10L)).thenReturn(Optional.of(aliceAccount));
        assertThat(accountSecurity.isOwner(10L, authentication)).isTrue();
    }

    @Test
    void isOwner_accountBelongsToOtherUser_returnsFalse() {
        Account bobAccount = buildAccount(20L, BOB_ID, "bob");
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
        Account bobAccount = buildAccount(20L, BOB_ID, "bob");
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
        assertThat(accountSecurity.isSelf(ALICE_ID, authentication)).isTrue();
    }

    @Test
    void isSelf_differentUserId_returnsFalse() {
        assertThat(accountSecurity.isSelf(BOB_ID, authentication)).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private Account buildAccount(Long id, UUID ownerId, String ownerUsername) {
        Account a = new Account();
        a.setId(id);
        a.setAccountNumber("ACC-" + id);
        a.setCurrency(Currency.EUR);
        a.setBalance(BigDecimal.ZERO);
        a.setOwnerId(ownerId);
        a.setOwnerUsername(ownerUsername);
        return a;
    }

    private Operation buildOperation(Account account) {
        Operation op = new Operation();
        op.setAccount(account);
        op.setType(OperationType.TRANSFER_IN);
        op.setAmount(new BigDecimal("100.00"));
        op.setCurrency(account.getCurrency());
        op.setBalanceAfter(new BigDecimal("100.00"));
        return op;
    }
}
