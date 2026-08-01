package com.bankapp.controller;

import com.bankapp.dto.BankDtos.*;
import com.bankapp.exception.GlobalExceptionHandler;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
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

    // Only needed to satisfy IdempotencyStore's constructor dependency in this test slice - no
    // test here sends an Idempotency-Key header, so IdempotencyStore.execute() always takes the
    // no-op fast path and never actually calls into this mock.
    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

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
        when(accountService.getAccountsByUser(USER_1)).thenReturn(accounts);

        mockMvc.perform(get("/api/accounts/user/{userId}", USER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].currency").value("EUR"))
                .andExpect(jsonPath("$[1].currency").value("USD"));
    }

    @Test
    void getAccountsByUser_otherUser_returns403() throws Exception {
        when(accountService.getAccountsByUser(USER_99))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(get("/api/accounts/user/{userId}", USER_99))
                .andExpect(status().isForbidden());
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

    // ── POST /api/accounts/{accountId}/transfer ───────────────────────────

    @Test
    void transfer_validRequest_returnsTwoTransactions() throws Exception {
        OperationResponse outTx = txResponse(UUID.randomUUID(), ACCOUNT_10, "ACC-AAAAAAAA", OperationType.TRANSFER_OUT,
                new BigDecimal("50.00"), Currency.EUR, new BigDecimal("950.00"), "Transfer to bob (ACC-BBBBBBBB)");
        OperationResponse inTx = txResponse(UUID.randomUUID(), ACCOUNT_20, "ACC-BBBBBBBB", OperationType.TRANSFER_IN,
                new BigDecimal("50.00"), Currency.EUR, new BigDecimal("550.00"), "Transfer from alice (ACC-AAAAAAAA)");

        when(accountService.resolveAccountId(ACCOUNT_10)).thenReturn(10L);
        when(accountService.transfer(eq(10L), any())).thenReturn(List.of(outTx, inTx));

        mockMvc.perform(post("/api/accounts/{accountId}/transfer", ACCOUNT_10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("amount", 50.00, "targetUsername", "bob", "targetAccountNumber", "ACC-BBBBBBBB"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$[1].type").value("TRANSFER_IN"));
    }

    @Test
    void transfer_recipientNotFound_returns404() throws Exception {
        when(accountService.resolveAccountId(ACCOUNT_10)).thenReturn(10L);
        when(accountService.transfer(eq(10L), any()))
                .thenThrow(new ResourceNotFoundException("Recipient not found"));

        mockMvc.perform(post("/api/accounts/{accountId}/transfer", ACCOUNT_10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("amount", 50.00, "targetUsername", "nobody", "targetAccountNumber", "BOGUS"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_blankTargetUsername_returns400() throws Exception {
        mockMvc.perform(post("/api/accounts/{accountId}/transfer", ACCOUNT_10)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("amount", 50.00, "targetUsername", "", "targetAccountNumber", "ACC-BBBBBBBB"))))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/accounts/recipient ─────────────────────────────────────────

    @Test
    void checkRecipient_validRecipient_returns200() throws Exception {
        when(accountService.checkRecipient("bob", "ACC-BBBBBBBB"))
                .thenReturn(new RecipientCheckResponse(true));

        mockMvc.perform(get("/api/accounts/recipient")
                        .param("username", "bob")
                        .param("accountNumber", "ACC-BBBBBBBB"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void checkRecipient_unknownRecipient_returns200WithValidFalse() throws Exception {
        when(accountService.checkRecipient("nobody", "BOGUS"))
                .thenReturn(new RecipientCheckResponse(false));

        mockMvc.perform(get("/api/accounts/recipient")
                        .param("username", "nobody")
                        .param("accountNumber", "BOGUS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void checkRecipient_missingQueryParam_returns400() throws Exception {
        mockMvc.perform(get("/api/accounts/recipient").param("username", "bob"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/accounts/{accountId}/transactions ────────────────────────

    @Test
    void getTransactionHistoryPaged_returnsPage() throws Exception {
        OperationResponse tx = txResponse(UUID.randomUUID(), ACCOUNT_10, "ACC-AAAAAAAA", OperationType.TRANSFER_IN,
                new BigDecimal("100.00"), Currency.EUR, new BigDecimal("1100.00"), "Credit");
        OperationPage page = new OperationPage(List.of(tx), 0, 10, 1L, true);
        when(accountService.resolveAccountId(ACCOUNT_10)).thenReturn(10L);
        when(accountService.getTransactionHistoryPaged(eq(10L), any())).thenReturn(page);

        mockMvc.perform(get("/api/accounts/{accountId}/transactions", ACCOUNT_10))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── GET /api/accounts/transactions/{transactionId} ────────────────────

    @Test
    void getTransaction_existingId_returnsTransaction() throws Exception {
        OperationResponse tx = txResponse(TX_5, ACCOUNT_10, "ACC-AAAAAAAA", OperationType.TRANSFER_OUT,
                new BigDecimal("50.00"), Currency.EUR, new BigDecimal("950.00"), "Coffee");
        when(accountService.resolveOperationId(TX_5)).thenReturn(5L);
        when(accountService.getTransaction(5L)).thenReturn(tx);

        mockMvc.perform(get("/api/accounts/transactions/{transactionId}", TX_5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TX_5.toString()))
                .andExpect(jsonPath("$.type").value("TRANSFER_OUT"));
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
