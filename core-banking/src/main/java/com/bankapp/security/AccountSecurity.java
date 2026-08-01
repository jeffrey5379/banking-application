package com.bankapp.security;

import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.Account;
import com.bankapp.model.Operation;
import com.bankapp.repository.AccountRepository;
import com.bankapp.repository.OperationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AccountSecurity {

    private final AccountRepository accountRepository;
    private final OperationRepository operationRepository;

    @Transactional(readOnly = true)
    public boolean isOwner(Long accountId, Authentication auth) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        return account.getOwnerId().equals(callerId(auth));
    }

    @Transactional(readOnly = true)
    public boolean isOwnerOfTransaction(Long operationId, Authentication auth) {
        Operation op = operationRepository.findById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + operationId));
        return op.getAccount().getOwnerId().equals(callerId(auth));
    }

    public boolean isSelf(UUID userId, Authentication auth) {
        return userId.equals(callerId(auth));
    }

    private UUID callerId(Authentication auth) {
        return ((JwtPrincipal) auth.getPrincipal()).getUserId();
    }
}
