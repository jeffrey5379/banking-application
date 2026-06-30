package com.bankapp.controller;

import com.bankapp.model.Currency;
import com.bankapp.service.ExchangeRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ExchangeRateController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ExchangeRateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExchangeRateService exchangeRateService;

    // ── GET /api/exchange-rates ───────────────────────────────────────────

    @Test
    void getAllRates_returnsMapOfRates() throws Exception {
        Map<String, BigDecimal> rates = Map.of(
                "EUR_USD", new BigDecimal("1.086957"),
                "USD_EUR", new BigDecimal("0.920000")
        );
        when(exchangeRateService.getAllRates()).thenReturn(rates);

        mockMvc.perform(get("/api/exchange-rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.EUR_USD").value(1.086957))
                .andExpect(jsonPath("$.USD_EUR").value(0.920000));
    }

    // ── GET /api/exchange-rates/{from}/{to} ───────────────────────────────

    @Test
    void getRate_eurToUsd_returnsRateResponse() throws Exception {
        when(exchangeRateService.getRate(Currency.EUR, Currency.USD))
                .thenReturn(new BigDecimal("1.086957"));

        mockMvc.perform(get("/api/exchange-rates/EUR/USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("EUR"))
                .andExpect(jsonPath("$.to").value("USD"))
                .andExpect(jsonPath("$.rate").value(1.086957));
    }

    @Test
    void getRate_sameCurrency_returnsOne() throws Exception {
        when(exchangeRateService.getRate(Currency.EUR, Currency.EUR))
                .thenReturn(BigDecimal.ONE);

        mockMvc.perform(get("/api/exchange-rates/EUR/EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(1));
    }

    @Test
    void getRate_invalidCurrency_returns400() throws Exception {
        mockMvc.perform(get("/api/exchange-rates/INVALID/USD"))
                .andExpect(status().isBadRequest());
    }
}
