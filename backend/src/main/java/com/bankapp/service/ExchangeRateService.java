package com.bankapp.service;

import com.bankapp.exception.ResourceNotFoundException;
import com.bankapp.model.Currency;
import com.bankapp.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;

    @Cacheable(value = "exchange-rate", key = "#from.name() + '_' + #to.name()")
    @Transactional(readOnly = true)
    public BigDecimal getRate(Currency from, Currency to) {
        if (from == to) return BigDecimal.ONE;
        BigDecimal fromInEur = rateToEur(from);
        BigDecimal toInEur = rateToEur(to);
        return fromInEur.divide(toInEur, 6, RoundingMode.HALF_UP);
    }

    public BigDecimal convert(BigDecimal amount, Currency from, Currency to) {
        return amount.multiply(getRate(from, to)).setScale(4, RoundingMode.HALF_UP);
    }

    @Cacheable("exchange-rates")
    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getAllRates() {
        Map<Currency, BigDecimal> ratesToEur = new HashMap<>();
        exchangeRateRepository.findAll()
                .forEach(r -> ratesToEur.put(r.getCurrency(), r.getRateToEur()));

        Map<String, BigDecimal> result = new HashMap<>();
        for (Currency from : ratesToEur.keySet()) {
            for (Currency to : ratesToEur.keySet()) {
                BigDecimal rate = from == to
                        ? BigDecimal.ONE
                        : ratesToEur.get(from).divide(ratesToEur.get(to), 6, RoundingMode.HALF_UP);
                result.put(from + "_" + to, rate);
            }
        }
        return result;
    }

    private BigDecimal rateToEur(Currency currency) {
        return exchangeRateRepository.findByCurrency(currency)
                .orElseThrow(() -> new ResourceNotFoundException("Exchange rate not found for: " + currency))
                .getRateToEur();
    }
}
