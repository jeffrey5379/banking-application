package com.bankapp.controller;

import com.bankapp.dto.BankDtos.*;
import com.bankapp.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.PageRequest;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountSummaryResponse> createAccount(@Valid @RequestBody CreateAccountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(req));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AccountSummaryResponse>> getAccountsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(accountService.getAccountsByUser(userId));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountSummaryResponse> getAccountSummary(@PathVariable Long accountId) {
        return ResponseEntity.ok(accountService.getAccountSummary(accountId));
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<OperationResponse> credit(
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyRequest req) {
        return ResponseEntity.ok(accountService.credit(accountId, req));
    }

    @PostMapping("/{accountId}/debit")
    public ResponseEntity<OperationResponse> debit(
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyRequest req) {
        return ResponseEntity.ok(accountService.debit(accountId, req));
    }

    @PostMapping("/{accountId}/exchange")
    public ResponseEntity<List<OperationResponse>> exchange(
            @PathVariable Long accountId,
            @Valid @RequestBody ExchangeRequest req) {
        return ResponseEntity.ok(accountService.exchange(accountId, req));
    }

    @GetMapping("/{accountId}/balance-history")
    public ResponseEntity<List<BalancePoint>> getBalanceHistory(@PathVariable Long accountId) {
        return ResponseEntity.ok(accountService.getBalanceHistory(accountId));
    }

    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<OperationPage> getTransactionHistory(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(accountService.getTransactionHistoryPaged(accountId, PageRequest.of(page, size)));
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<OperationResponse> getTransaction(@PathVariable Long transactionId) {
        return ResponseEntity.ok(accountService.getTransaction(transactionId));
    }

    @GetMapping("/{accountId}/summary")
    public ResponseEntity<AccountStatsResponse> getAccountStats(@PathVariable Long accountId) {
        return ResponseEntity.ok(accountService.getAccountStats(accountId));
    }
}
