package com.bankapp.controller;

import com.bankapp.dto.BankDtos.*;
import com.bankapp.service.AccountService;
import com.bankapp.service.IdempotencyStore;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final IdempotencyStore idempotencyStore;

    @PostMapping
    public ResponseEntity<AccountSummaryResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        AccountSummaryResponse response = idempotencyStore.execute(idempotencyKey, "createAccount",
                () -> accountService.createAccount(req));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccountSummaryResponse>> getAccountsByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(accountService.getAccountsByUser(accountService.resolveUserId(userId)));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountSummaryResponse> getAccountSummary(@PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.getAccountSummary(accountService.resolveAccountId(accountId)));
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<OperationResponse> credit(
            @PathVariable UUID accountId,
            @Valid @RequestBody MoneyRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long id = accountService.resolveAccountId(accountId);
        OperationResponse response = idempotencyStore.execute(idempotencyKey, "credit:" + accountId,
                () -> accountService.credit(id, req));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountId}/debit")
    public ResponseEntity<OperationResponse> debit(
            @PathVariable UUID accountId,
            @Valid @RequestBody MoneyRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long id = accountService.resolveAccountId(accountId);
        OperationResponse response = idempotencyStore.execute(idempotencyKey, "debit:" + accountId,
                () -> accountService.debit(id, req));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{accountId}/exchange")
    public ResponseEntity<List<OperationResponse>> exchange(
            @PathVariable UUID accountId,
            @Valid @RequestBody ExchangeRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long id = accountService.resolveAccountId(accountId);
        List<OperationResponse> response = idempotencyStore.execute(idempotencyKey, "exchange:" + accountId,
                () -> accountService.exchange(id, req));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}/balance-history")
    public ResponseEntity<List<BalancePoint>> getBalanceHistory(@PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.getBalanceHistory(accountService.resolveAccountId(accountId)));
    }

    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<OperationPage> getTransactionHistory(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(accountService.getTransactionHistoryPaged(
                accountService.resolveAccountId(accountId), PageRequest.of(page, size)));
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<OperationResponse> getTransaction(@PathVariable UUID transactionId) {
        return ResponseEntity.ok(accountService.getTransaction(accountService.resolveOperationId(transactionId)));
    }

    @GetMapping("/{accountId}/summary")
    public ResponseEntity<AccountStatsResponse> getAccountStats(@PathVariable UUID accountId) {
        return ResponseEntity.ok(accountService.getAccountStats(accountService.resolveAccountId(accountId)));
    }
}
