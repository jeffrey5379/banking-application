package com.bankapp.controller;

import com.bankapp.dto.BankDtos.*;
import com.bankapp.exception.GlobalExceptionHandler;
import com.bankapp.exception.InsufficientFundsException;
import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.Currency;
import com.bankapp.model.OperationType;
import com.bankapp.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AccountController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@Import(GlobalExceptionHandler.class)
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    @Autowired
    private ObjectMapper objectMapper;

    // ── POST /api/accounts ────────────────────────────────────────────────

    @Test
    void createAccount_validRequest_returns201() throws Exception {
        AccountSummaryResponse summary = new AccountSummaryResponse(10L, "ACC-AAAAAAAA",
                Currency.EUR, BigDecimal.ZERO, 1L, "alice");
        when(accountService.createAccount(any())).thenReturn(summary);

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currency", "EUR"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
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
                new AccountSummaryResponse(10L, "ACC-AAAAAAAA", Currency.EUR, BigDecimal.ZERO, 1L, "alice"),
                new AccountSummaryResponse(20L, "ACC-BBBBBBBB", Currency.USD, BigDecimal.ZERO, 1L, "alice")
        );
        when(accountService.getAccountsByUser(1L)).thenReturn(accounts);

        mockMvc.perform(get("/api/accounts/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[1].currency").value("USD"));
    }

    @Test
    void getAccountsByUser_otherUser_returns403() throws Exception {
        when(accountService.getAccountsByUser(99L))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/accounts/user/99"))
                .andExpect(status().isForbidden());
    }

    // ── POST /api/accounts/{accountId}/credit ─────────────────────────────

    @Test
    void credit_validRequest_returnsTransaction() throws Exception {
        OperationResponse tx = txResponse(1L, 10L, "ACC-AAAAAAAA", OperationType.CREDIT,
                new BigDecimal("200.00"), Currency.EUR, new BigDecimal("1200.00"), "Salary");
        when(accountService.credit(eq(10L), any())).thenReturn(tx);

        mockMvc.perform(post("/api/accounts/10/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 200.00, "description", "Salary"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.amount").value(200.00))
                .andExpect(jsonPath("$.description").value("Salary"));
    }

    @Test
    void credit_zeroAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/10/credit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 0.00))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/accounts/{accountId}/debit ──────────────────────────────

    @Test
    void debit_sufficientFunds_returnsTransaction() throws Exception {
        OperationResponse tx = txResponse(2L, 10L, "ACC-AAAAAAAA", OperationType.DEBIT,
                new BigDecimal("300.00"), Currency.EUR, new BigDecimal("700.00"), "Rent");
        when(accountService.debit(eq(10L), any())).thenReturn(tx);

        mockMvc.perform(post("/api/accounts/10/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 300.00, "description", "Rent"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("DEBIT"))
                .andExpect(jsonPath("$.balanceAfter").value(700.00));
    }

    @Test
    void debit_insufficientFunds_returns400() throws Exception {
        when(accountService.debit(eq(10L), any()))
                .thenThrow(new InsufficientFundsException("Insufficient funds. Balance: 1000.00 EUR"));

        mockMvc.perform(post("/api/accounts/10/debit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 9999.00))))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/accounts/{accountId}/exchange ───────────────────────────

    @Test
    void exchange_validRequest_returnsTwoTransactions() throws Exception {
        OperationResponse outTx = txResponse(3L, 10L, "ACC-AAAAAAAA", OperationType.EXCHANGE_OUT,
                new BigDecimal("100.00"), Currency.EUR, new BigDecimal("900.00"), "Exchange EUR → USD");
        OperationResponse inTx = txResponse(4L, 20L, "ACC-BBBBBBBB", OperationType.EXCHANGE_IN,
                new BigDecimal("108.6957"), Currency.USD, new BigDecimal("608.6957"), "Exchange EUR → USD");

        when(accountService.exchange(eq(10L), any())).thenReturn(List.of(outTx, inTx));

        mockMvc.perform(post("/api/accounts/10/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("amount", 100.00, "targetAccountId", 20))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("EXCHANGE_OUT"))
                .andExpect(jsonPath("$[1].type").value("EXCHANGE_IN"));
    }

    // ── GET /api/accounts/{accountId}/transactions ────────────────────────

    @Test
    void getTransactionHistoryPaged_returnsPage() throws Exception {
        OperationResponse tx = txResponse(1L, 10L, "ACC-AAAAAAAA", OperationType.CREDIT,
                new BigDecimal("100.00"), Currency.EUR, new BigDecimal("1100.00"), "Credit");
        OperationPage page = new OperationPage(List.of(tx), 0, 10, 1L, true);
        when(accountService.getTransactionHistoryPaged(eq(10L), any())).thenReturn(page);

        mockMvc.perform(get("/api/accounts/10/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("CREDIT"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── GET /api/accounts/transactions/{transactionId} ────────────────────

    @Test
    void getTransaction_existingId_returnsTransaction() throws Exception {
        OperationResponse tx = txResponse(5L, 10L, "ACC-AAAAAAAA", OperationType.DEBIT,
                new BigDecimal("50.00"), Currency.EUR, new BigDecimal("950.00"), "Coffee");
        when(accountService.getTransaction(5L)).thenReturn(tx);

        mockMvc.perform(get("/api/accounts/transactions/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.type").value("DEBIT"));
    }

    @Test
    void getTransaction_notFound_returns404() throws Exception {
        when(accountService.getTransaction(99L))
                .thenThrow(new ResourceNotFoundException("Transaction not found: 99"));

        mockMvc.perform(get("/api/accounts/transactions/99"))
                .andExpect(status().isNotFound());
    }

    // ── helper ────────────────────────────────────────────────────────────

    private OperationResponse txResponse(Long id, Long accountId, String accountNumber,
                                         OperationType type, BigDecimal amount,
                                         Currency currency, BigDecimal balanceAfter,
                                         String description) {
        return new OperationResponse(id, accountId, accountNumber, type, amount, currency,
                balanceAfter, description, LocalDateTime.now(), null, null);
    }
}
