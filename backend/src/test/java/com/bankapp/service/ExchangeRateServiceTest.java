package com.bankapp.service;

import com.bankapp.model.Currency;
import com.bankapp.model.ExchangeRate;
import com.bankapp.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExchangeRateServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    private ExchangeRateService exchangeRateService;

    private static final List<ExchangeRate> ALL_RATES = List.of(
            rate(Currency.EUR, "1.00000000"),
            rate(Currency.USD, "0.92000000"),
            rate(Currency.CHF, "1.05000000"),
            rate(Currency.GBP, "1.17000000"),
            rate(Currency.SEK, "0.08700000"),
            rate(Currency.VND, "0.00003700")
    );

    private static ExchangeRate rate(Currency currency, String rateToEur) {
        return new ExchangeRate(currency, new BigDecimal(rateToEur));
    }

    @BeforeEach
    void setUp() {
        exchangeRateService = new ExchangeRateService(exchangeRateRepository);
        ALL_RATES.forEach(r ->
                when(exchangeRateRepository.findByCurrency(r.getCurrency())).thenReturn(Optional.of(r)));
        when(exchangeRateRepository.findAll()).thenReturn(ALL_RATES);
    }

    // ── getRate ───────────────────────────────────────────────────────────

    @Test
    void getRate_sameCurrency_returnsOne() {
        assertThat(exchangeRateService.getRate(Currency.EUR, Currency.EUR))
                .isEqualByComparingTo(BigDecimal.ONE);
        assertThat(exchangeRateService.getRate(Currency.USD, Currency.USD))
                .isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void getRate_eurToUsd_correctRate() {
        // EUR=1.0, USD=0.92 -> 1.0/0.92 = 1.086957
        BigDecimal rate = exchangeRateService.getRate(Currency.EUR, Currency.USD);
        assertThat(rate).isEqualByComparingTo(new BigDecimal("1.086957"));
    }

    @Test
    void getRate_usdToEur_correctRate() {
        // USD=0.92, EUR=1.0 -> 0.92/1.0 = 0.920000
        BigDecimal rate = exchangeRateService.getRate(Currency.USD, Currency.EUR);
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.920000"));
    }

    @Test
    void getRate_gbpToChf_correctRate() {
        // GBP=1.17, CHF=1.05 -> 1.17/1.05 = 1.114286
        BigDecimal rate = exchangeRateService.getRate(Currency.GBP, Currency.CHF);
        assertThat(rate).isEqualByComparingTo(new BigDecimal("1.114286"));
    }

    @Test
    void getRate_chfToGbp_correctRate() {
        // CHF=1.05, GBP=1.17 -> 1.05/1.17 = 0.897436
        BigDecimal rate = exchangeRateService.getRate(Currency.CHF, Currency.GBP);
        assertThat(rate).isEqualByComparingTo(new BigDecimal("0.897436"));
    }

    // ── convert ───────────────────────────────────────────────────────────

    @Test
    void convert_sameCurrency_returnsSameAmount() {
        BigDecimal result = exchangeRateService.convert(new BigDecimal("100.00"), Currency.EUR, Currency.EUR);
        assertThat(result).isEqualByComparingTo(new BigDecimal("100.0000"));
    }

    @Test
    void convert_eurToUsd_correctAmount() {
        // 100 EUR * 1.086957 = 108.6957 USD
        BigDecimal result = exchangeRateService.convert(new BigDecimal("100"), Currency.EUR, Currency.USD);
        assertThat(result).isEqualByComparingTo(new BigDecimal("108.6957"));
    }

    @Test
    void convert_usdToEur_correctAmount() {
        // 200 USD * 0.92 = 184.0000 EUR
        BigDecimal result = exchangeRateService.convert(new BigDecimal("200"), Currency.USD, Currency.EUR);
        assertThat(result).isEqualByComparingTo(new BigDecimal("184.0000"));
    }

    @Test
    void convert_gbpToEur_correctAmount() {
        // 50 GBP * 1.17 = 58.5000 EUR
        BigDecimal result = exchangeRateService.convert(new BigDecimal("50"), Currency.GBP, Currency.EUR);
        assertThat(result).isEqualByComparingTo(new BigDecimal("58.5000"));
    }

    @Test
    void convert_scaleIsFour() {
        BigDecimal result = exchangeRateService.convert(new BigDecimal("1"), Currency.EUR, Currency.USD);
        assertThat(result.scale()).isEqualTo(4);
    }

    // ── getAllRates ───────────────────────────────────────────────────────

    @Test
    void getAllRates_containsAllCurrencyPairs() {
        Map<String, BigDecimal> rates = exchangeRateService.getAllRates();
        // 6 currencies x 6 currencies = 36 pairs
        assertThat(rates).hasSize(36);
    }

    @Test
    void getAllRates_containsSameCurrencyPairsAsOne() {
        Map<String, BigDecimal> rates = exchangeRateService.getAllRates();
        assertThat(rates.get("EUR_EUR")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(rates.get("USD_USD")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(rates.get("GBP_GBP")).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(rates.get("CHF_CHF")).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void getAllRates_containsExpectedPairs() {
        Map<String, BigDecimal> rates = exchangeRateService.getAllRates();
        assertThat(rates).containsKey("EUR_USD");
        assertThat(rates).containsKey("USD_GBP");
        assertThat(rates).containsKey("GBP_CHF");
        assertThat(rates.get("EUR_USD")).isEqualByComparingTo(new BigDecimal("1.086957"));
    }
}
