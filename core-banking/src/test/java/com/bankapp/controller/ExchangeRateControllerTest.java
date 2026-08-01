package com.bankapp.controller;

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

}
