package com.bankapp.repository;

import com.bankapp.model.Account;
import com.bankapp.model.Currency;
import com.bankapp.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED) // disable auto-rollback: we need real separate transactions
class AccountRepositoryTest {

    @Autowired
    AccountRepository accountRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PlatformTransactionManager txManager;

    private Long accountId;

    @BeforeEach
    void setUp() {
        new TransactionTemplate(txManager).execute(status -> {
            User user = new User();
            user.setUsername("locktest");
            user.setEmail("lock@test.com");
            user.setPassword("password");
            userRepository.save(user);

            Account account = new Account();
            account.setAccountNumber("ACC-LOCKTEST");
            account.setCurrency(Currency.EUR);
            account.setBalance(new BigDecimal("1000.00"));
            account.setUser(user);
            accountId = accountRepository.save(account).getId();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        new TransactionTemplate(txManager).execute(status -> {
            accountRepository.deleteAll();
            userRepository.deleteAll();
            return null;
        });
    }

    // ── concurrent modification ───────────────────────────────────────────

    @Test
    void concurrentModification_throwsOptimisticLockingFailure() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        // Tx1: read account — stale[0] is detached after commit with version=0
        final Account[] stale = {null};
        tx.execute(status -> {
            stale[0] = accountRepository.findById(accountId).get();
            return null;
        });

        // Tx2: another request commits first — version bumped to 1 in DB
        tx.execute(status -> {
            Account a = accountRepository.findById(accountId).get();
            a.setBalance(new BigDecimal("900.00"));
            accountRepository.save(a);
            return null;
        });

        // Tx3: try to commit stale copy (version=0) — DB has version=1 → 409 Conflict
        assertThatThrownBy(() ->
            tx.execute(status -> {
                stale[0].setBalance(new BigDecimal("800.00"));
                accountRepository.saveAndFlush(stale[0]);
                return null;
            })
        ).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void firstCommitWins_secondReadReflectsWinner() {
        TransactionTemplate tx = new TransactionTemplate(txManager);

        final Account[] stale = {null};
        tx.execute(status -> {
            stale[0] = accountRepository.findById(accountId).get();
            return null;
        });

        // Winner: deducted 100
        tx.execute(status -> {
            Account a = accountRepository.findById(accountId).get();
            a.setBalance(new BigDecimal("900.00"));
            accountRepository.save(a);
            return null;
        });

        // Loser throws; after retry we read the committed state
        try {
            tx.execute(status -> {
                stale[0].setBalance(new BigDecimal("800.00"));
                accountRepository.saveAndFlush(stale[0]);
                return null;
            });
        } catch (OptimisticLockingFailureException ignored) {}

        Account afterConflict = accountRepository.findById(accountId).get();
        assertThat(afterConflict.getBalance()).isEqualByComparingTo(new BigDecimal("900.00"));
        assertThat(afterConflict.getVersion()).isEqualTo(1L);
    }
}
