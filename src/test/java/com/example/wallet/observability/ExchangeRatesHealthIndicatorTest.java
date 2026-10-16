package com.example.wallet.observability;

import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.service.ExchangeRateService;
import com.example.wallet.service.RatesSnapshot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExchangeRatesHealthIndicatorTest {

    private static final LocalDate FIXING_DATE = LocalDate.of(2026, 10, 15);
    private static final Instant LAST_CHECKED_AT = Instant.parse("2026-10-16T13:00:02Z");

    private final ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);
    private final ExchangeRatesHealthIndicator indicator = new ExchangeRatesHealthIndicator(exchangeRateService);

    @Test
    void upWithFixingDateWhenRatesAreCached() {
        when(exchangeRateService.cachedRates()).thenReturn(Optional.of(snapshot(false)));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("validFor", FIXING_DATE)
                .containsEntry("stale", false)
                .containsEntry("lastCheckedAt", LAST_CHECKED_AT);
    }

    @Test
    void staleRatesAreStillUp() {
        when(exchangeRateService.cachedRates()).thenReturn(Optional.of(snapshot(true)));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("stale", true);
    }

    @Test
    void unknownWhenNoRatesAreLoaded() {
        when(exchangeRateService.cachedRates()).thenReturn(Optional.empty());

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    void neverCallsTheProvider() {
        when(exchangeRateService.cachedRates()).thenReturn(Optional.empty());

        indicator.health();

        verify(exchangeRateService, never()).currentRates();
    }

    private static RatesSnapshot snapshot(boolean stale) {
        return new RatesSnapshot(new ExchangeRates(FIXING_DATE, Map.of()), stale, LAST_CHECKED_AT);
    }
}
