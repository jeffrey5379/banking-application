package com.bankapp.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        // exchange-rates: full rates map — one entry, refreshed every 5 minutes
        CaffeineCache allRatesCache = new CaffeineCache("exchange-rates",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(1)
                        .recordStats()
                        .build());

        // exchange-rate: individual from→to pairs (4×4 = 16 max), same TTL
        CaffeineCache singleRateCache = new CaffeineCache("exchange-rate",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(16)
                        .recordStats()
                        .build());

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(allRatesCache, singleRateCache));
        return manager;
    }
}
