package com.example.wallet.observability;

import com.example.wallet.service.ExchangeRateService;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class ExchangeRatesHealthIndicator implements HealthIndicator {

    private final ExchangeRateService exchangeRateService;

    public ExchangeRatesHealthIndicator(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @Override
    public Health health() {
        return exchangeRateService.cachedRates()
                .map(snapshot -> Health.up()
                        .withDetail("validFor", snapshot.rates().validFor())
                        .withDetail("stale", snapshot.stale())
                        .withDetail("lastCheckedAt", snapshot.lastCheckedAt())
                        .build())
                .orElseGet(() -> Health.unknown()
                        .withDetail("reason", "No exchange rates loaded yet")
                        .build());
    }
}
