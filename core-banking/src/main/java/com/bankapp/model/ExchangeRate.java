package com.bankapp.model;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rates")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = "currency")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private Currency currency;

    // How many EUR equals 1 unit of this currency
    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal rateToEur;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ExchangeRate(Currency currency, BigDecimal rateToEur) {
        this.currency = currency;
        this.rateToEur = rateToEur;
    }
}
