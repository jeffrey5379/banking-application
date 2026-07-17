package com.bankapp.controller;

import com.bankapp.dto.BankDtos.*;
import com.bankapp.exception.GlobalExceptionHandler;
import com.bankapp.exception.InsufficientFundsException;
import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.Currency;
import com.bankapp.model.OperationType;
import com.bankapp.service.AccountService;
import com.bankapp.service.IdempotencyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AccountController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import({GlobalExceptionHandler.class, IdempotencyStore.class})
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Public-facing UUIDs used in URLs; each maps to an internal Long id via resolve*() stubs.
    private static final UUID ACCOUNT_10 = UUID.randomUUID();
    private static final UUID ACCOUNT_20 = UUID.randomUUID();
    private static final UUID USER_1 = UUID.randomUUID();
    private static final UUID USER_99 = UUID.randomUUID();
    private static final UUID TX_5 = UUID.randomUUID();
    private static final UUID TX_99 = UUID.randomUUID();

    // ── POST /api/accounts ────────────────────────────────────────────────

    @Test
    void createAccount_validRequest_returns201() throws Exception {
        AccountSummaryResponse summary = new AccountSummaryResponse(ACCOUNT_10, "ACC-AAAAAAAA",
                Currency.EUR, BigDecimal.ZERO, USER_1, "alice");
        when(accountService.createAccount(any())).thenReturn(summary);

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currency", "EUR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ACCOUNT_10.toString()))
                .andExpect(jsonPath("$.accountNumber").value("ACC-AAAAAAAA"))
                .andExpect(jsonPath("$.currency").value("EUR"));
    }

    @Test
    void createAccount_missingCurrency_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/accounts/user/{userId} ───────────────────────────────────

    @Test
    void getAccountsByUser_validUser_returnsAccounts() throws Exception {
        List<AccountSummaryResponse> accounts = List.of(
                new AccountSummaryResponse(ACCOUNT_10, "ACC-AAAAAAAA", Currency.EUR, BigDecimal.ZERO, USER_1, "alice"),
                new AccountSummaryResponse(ACCOUNT_20, "ACC-BBBBBBBB", Currency.USD, BigDecimal.ZERO, USER_1, "alice")
        );
        when(accountService.resolveUserId(USER_1)).thenReturn(1L);
        when(accountService.getAccountsByUser(1L)).thenReturn(accounts);

        mockMvc.perform(get("/api/accounts/user/{userId}", USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[1].currency").value("USD"));
    }

    @Test
    void getAccountsByUser_otherUser_returns403() throws Exception {
        when(accountService.resolveUserId(USER_99)).thenReturn(99L);
        when(accountService.getAccountsByUser(99L))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/accounts/user/{userId}", USER_99))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/accounts/{accountId}/credit ─────────────────────────────

    @Test
    void credit_validRequest_returnsTransaction() throws Exception {
        OperationResponse tx = txResponse(UUID.randomUUID(), ACCOUNT_10, "ACC-AAAAAAAA", OperationType.CREDIT,
                new BigDecimal("200.00"), Currency.EUR, new BigDecimal("1200.00"), "Salary");
        when(accountService.resolveAccountId(ACCOUNT_10)).thenReturn(10L);
        when(accountService.credit(eq(10L), any())).thenReturn(tx);

        mockMvc.perform(post("/api/accounts/{accountId}/credit", ACCOUNT_10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 200.00, "description", "Salary"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.description").value("Salary"));
    }

    @Test
    void credit_repeatedIdempotencyKey_invokesServiceOnceAndReplaysResponse() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null));

        OperationResponse tx = txResponse(UUID.randomUUID(), ACCOUNT_10, "ACC-AAAAAAAA", OperationType.CREDIT,
                new BigDecimal("200.00"), Currency.EUR, new BigDecimal("1200.00"), "Salary");
        when(accountService.resolveAccountId(ACCOUNT_10)).thenReturn(10L);
        when(accountService.credit(eq(10L), any())).thenReturn(tx);

        String body = objectMapper.writeValueAsString(Map.of("amount", 200.00, "description", "Salary"));
        String idempotencyKey = UUID.randomUUID().toString();

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/accounts/{accountId}/credit", ACCOUNT_10)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(tx.id().toString()));
        }

        verify(accountService, times(1)).credit(eq(10L), any());
    }

    @Test
    void credit_zeroAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/{accountId}/credit", ACCOUNT_10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 0.00))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/accounts/{accountId}/debit ──────────────────────────────

    @Test
    void debit_sufficientFunds_returnsTransaction() throws Exception {
        OperationResponse tx = txResponse(UUID.randomUUID(), ACCOUNT_10, "ACC-AAAAAAAA", OperationType.DEBIT,
                new BigDecimal("300.00"), Currency.EUR, new BigDecimal("700.00"), "Rent");
        when(accountService.resolveAccountId(ACCOUNT_10)).thenReturn(10L);
        when(accountService.debit(eq(10L), any())).thenReturn(tx);

        mockMvc.perform(post("/api/accounts/{accountId}/debit", ACCOUNT_10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 300.00, "description", "Rent"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEBIT"))
                .andExpect(jsonPath("$.balanceAfter").value(700.00));
    }

    @Test
    void debit_insufficientFunds_returns400() throws Exception {
        when(accountService.resolveAccountId(ACCOUNT_10)).thenReturn(10L);
        when(accountService.debit(eq(10L), any()))
                .thenThrow(new InsufficientFundsException("Insufficient funds. Balance: 1000.00 EUR"));

        mockMvc.perform(post("/api/accounts/{accountId}/debit", ACCOUNT_10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 9999.00))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/accounts/{accountId}/exchange ───────────────────────────

    @Test
    void exchange_validRequest_returnsTwoTransactions() throws Exception {
        OperationResponse outTx = txResponse(UUID.randomUUID(), ACCOUNT_10, "ACC-AAAAAAAA", OperationType.EXCHANGE_OUT,
                new BigDecimal("100.00"), Currency.EUR, new BigDecimal("900.00"), "Exchange EUR → USD");
        OperationResponse inTx = txResponse(UUID.randomUUID(), ACCOUNT_20, "ACC-BBBBBBBB", OperationType.EXCHANGE_IN,
                new BigDecimal("108.6957"), Currency.USD, new BigDecimal("608.6957"), "Exchange EUR → USD");

        when(accountService.resolveAccountId(ACCOUNT_10)).thenReturn(10L);
        when(accountService.exchange(eq(10L), any())).thenReturn(List.of(outTx, inTx));

        mockMvc.perform(post("/api/accounts/{accountId}/exchange", ACCOUNT_10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 100.00, "targetAccountId", ACCOUNT_20.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("EXCHANGE_OUT"))
                .andExpect(jsonPath("$[1].type").value("EXCHANGE_IN"));
    }

    @Test
    void exchange_unexpectedServerError_returns500WithGenericMessage() throws Exception {
        when(accountService.resolveAccountId(ACCOUNT_10)).thenReturn(10L);
        when(accountService.exchange(eq(10L), any())).thenThrow(new RuntimeException("Some DB error"));

        mockMvc.perform(post("/api/accounts/{accountId}/exchange", ACCOUNT_10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 100.00, "targetAccountId", ACCOUNT_20.toString()))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Some DB error"))));
    }

    // ── GET /api/accounts/{accountId}/transactions ────────────────────────

    @Test
    void getTransactionHistoryPaged_returnsPage() throws Exception {
        OperationResponse tx = txResponse(UUID.randomUUID(), ACCOUNT_10, "ACC-AAAAAAAA", OperationType.CREDIT,
                new BigDecimal("100.00"), Currency.EUR, new BigDecimal("1100.00"), "Credit");
        OperationPage page = new OperationPage(List.of(tx), 0, 10, 1L, true);
        when(accountService.resolveAccountId(ACCOUNT_10)).thenReturn(10L);
        when(accountService.getTransactionHistoryPaged(eq(10L), any())).thenReturn(page);

        mockMvc.perform(get("/api/accounts/{accountId}/transactions", ACCOUNT_10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("CREDIT"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── GET /api/accounts/transactions/{transactionId} ────────────────────

    @Test
    void getTransaction_existingId_returnsTransaction() throws Exception {
        OperationResponse tx = txResponse(TX_5, ACCOUNT_10, "ACC-AAAAAAAA", OperationType.DEBIT,
                new BigDecimal("50.00"), Currency.EUR, new BigDecimal("950.00"), "Coffee");
        when(accountService.resolveOperationId(TX_5)).thenReturn(5L);
        when(accountService.getTransaction(5L)).thenReturn(tx);

        mockMvc.perform(get("/api/accounts/transactions/{transactionId}", TX_5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TX_5.toString()))
                .andExpect(jsonPath("$.type").value("DEBIT"));
    }

    @Test
    void getTransaction_notFound_returns404() throws Exception {
        when(accountService.resolveOperationId(TX_99))
                .thenThrow(new ResourceNotFoundException("Transaction not found: " + TX_99));

        mockMvc.perform(get("/api/accounts/transactions/{transactionId}", TX_99))
                .andExpect(status().isNotFound());
    }

    // ── helper ────────────────────────────────────────────────────────────

    private OperationResponse txResponse(UUID id, UUID accountId, String accountNumber,
                                         OperationType type, BigDecimal amount,
                                         Currency currency, BigDecimal balanceAfter,
                                         String description) {
        return new OperationResponse(id, accountId, accountNumber, type, amount, currency,
                balanceAfter, description, LocalDateTime.now(), null, null, null);
    }
}
