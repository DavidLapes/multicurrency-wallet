package com.example.wallet.service;

import com.example.wallet.domain.ExchangeRates;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExchangeRateRefreshJobTest {

    private final ExchangeRateService exchangeRateService = mock(ExchangeRateService.class);

    @Test
    void refreshAsksTheServiceForCurrentRates() {
        when(exchangeRateService.currentRates()).thenReturn(snapshot());

        job(true).refresh();

        verify(exchangeRateService).currentRates();
    }

    @Test
    void loadsRatesOnStartupWhenEnabled() {
        when(exchangeRateService.currentRates()).thenReturn(snapshot());

        job(true).onStartup();

        verify(exchangeRateService).currentRates();
    }

    @Test
    void skipsStartupLoadWhenDisabled() {
        job(false).onStartup();

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void failedRefreshDoesNotPropagate() {
        when(exchangeRateService.currentRates()).thenThrow(new ExchangeRatesUnavailableException("CNB down"));

        assertThatCode(() -> job(true).refresh()).doesNotThrowAnyException();
    }

    private ExchangeRateRefreshJob job(boolean onStartup) {
        return new ExchangeRateRefreshJob(exchangeRateService, new ExchangeRateRefreshProperties("-", onStartup));
    }

    private static RatesSnapshot snapshot() {
        return new RatesSnapshot(new ExchangeRates(LocalDate.of(2026, 10, 16), Map.of()), false, Instant.parse("2026-10-16T13:00:00Z"));
    }
}
