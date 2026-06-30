package com.bankapp.controller;

import com.bankapp.dto.BankDtos.ExchangeRateResponse;
import com.bankapp.model.Currency;
import com.bankapp.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping
    public ResponseEntity<Map<String, BigDecimal>> getAllRates() {
        return ResponseEntity.ok(exchangeRateService.getAllRates());
    }

    @GetMapping("/{from}/{to}")
    public ResponseEntity<ExchangeRateResponse> getRate(
            @PathVariable Currency from,
            @PathVariable Currency to) {
        ExchangeRateResponse resp = new ExchangeRateResponse(from, to, exchangeRateService.getRate(from, to));
        return ResponseEntity.ok(resp);
    }
}
