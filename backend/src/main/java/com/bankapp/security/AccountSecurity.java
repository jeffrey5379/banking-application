package com.bankapp.security;

import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.Account;
import com.bankapp.model.Operation;
import com.bankapp.repository.AccountRepository;
import com.bankapp.repository.OperationRepository;
import com.bankapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AccountSecurity {

    private final AccountRepository accountRepository;
    private final OperationRepository operationRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public boolean isOwner(Long accountId, Authentication auth) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        return account.getUser().getUsername().equals(auth.getName());
    }

    @Transactional(readOnly = true)
    public boolean isOwnerOfTransaction(Long operationId, Authentication auth) {
        Operation op = operationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + operationId));
        return op.getAccount().getUser().getUsername().equals(auth.getName());
    }

    @Transactional(readOnly = true)
    public boolean isSelf(Long userId, Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .map(u -> u.getId().equals(userId))
                .orElse(false);
    }
}
