package com.example.wallet.service;

import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.support.MutableClock;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRateServiceTest {

    private static final ZoneId PRAGUE = ZoneId.of("Europe/Prague");
    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(10);

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 13);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 16);

    private final MutableClock clock = new MutableClock(pragueTime("2026-10-13T10:00"));
    private final ScriptedProvider provider = new ScriptedProvider();
    private final ExchangeRateService service = new ExchangeRateService(provider, clock,
            new ExchangeRateProperties(PRAGUE, LocalTime.of(14, 30), RETRY_INTERVAL));

    @Test
    void morningUsesYesterdaysFixingWithoutCallingAgain() {
        provider.willReturn(rates(MONDAY));

        service.currentRates();
        clock.setTo(pragueTime("2026-10-13T14:29"));
        service.currentRates();

        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void firstRequestAfterPublicationFetchesTodaysFixing() {
        provider.willReturn(rates(MONDAY)).willReturn(rates(TUESDAY));

        service.currentRates();
        clock.setTo(pragueTime("2026-10-13T14:30"));

        assertThat(service.currentRates().rates().validFor()).isEqualTo(TUESDAY);
        assertThat(provider.calls).isEqualTo(2);
    }

    @Test
    void todaysFixingIsKeptUntilTheNextPublication() {
        clock.setTo(pragueTime("2026-10-13T15:00"));
        provider.willReturn(rates(TUESDAY));

        service.currentRates();
        clock.setTo(pragueTime("2026-10-13T23:59"));
        service.currentRates();
        clock.setTo(pragueTime("2026-10-14T14:29"));
        service.currentRates();

        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void noCallsOverTheWeekend() {
        clock.setTo(pragueTime("2026-10-16T15:00"));
        provider.willReturn(rates(FRIDAY));

        service.currentRates();
        clock.setTo(pragueTime("2026-10-17T12:00"));   // Saturday
        service.currentRates();
        clock.setTo(pragueTime("2026-10-19T14:29"));   // Monday before publication
        service.currentRates();

        assertThat(provider.calls).isEqualTo(1);
    }

    @Test
    void latePublicationIsRetriedAfterTheRetryInterval() {
        clock.setTo(pragueTime("2026-10-13T14:31"));
        provider.willReturn(rates(MONDAY)).willReturn(rates(TUESDAY));

        service.currentRates();                         // CNB has not published yet
        clock.advance(RETRY_INTERVAL.minusSeconds(1));
        service.currentRates();
        assertThat(provider.calls).isEqualTo(1);

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.currentRates().rates().validFor()).isEqualTo(TUESDAY);
        assertThat(provider.calls).isEqualTo(2);
    }

    @Test
    void providerFailureFallsBackToCachedRatesMarkedStale() {
        provider.willReturn(rates(MONDAY)).willFail();

        service.currentRates();
        clock.setTo(pragueTime("2026-10-13T15:00"));
        RatesSnapshot snapshot = service.currentRates();

        assertThat(snapshot.stale()).isTrue();
        assertThat(snapshot.rates().validFor()).isEqualTo(MONDAY);
        assertThat(snapshot.lastCheckedAt()).isEqualTo(pragueTime("2026-10-13T15:00"));
    }

    @Test
    void staleFlagIsClearedByTheNextSuccessfulRefresh() {
        provider.willReturn(rates(MONDAY)).willFail().willReturn(rates(TUESDAY));

        service.currentRates();
        clock.setTo(pragueTime("2026-10-13T15:00"));
        service.currentRates();
        clock.advance(RETRY_INTERVAL);

        assertThat(service.currentRates().stale()).isFalse();
    }

    @Test
    void providerFailureWithEmptyCacheThrows() {
        provider.willFail();

        assertThatThrownBy(service::currentRates)
                .isInstanceOf(ExchangeRatesUnavailableException.class)
                .hasCauseInstanceOf(ExchangeRateProviderException.class);
    }

    @Test
    void cachedRatesNeverCallTheProvider() {
        provider.willReturn(rates(MONDAY));

        assertThat(service.cachedRates()).isEmpty();
        assertThat(provider.calls).isZero();

        service.currentRates();
        assertThat(service.cachedRates()).hasValueSatisfying(s -> assertThat(s.rates().validFor()).isEqualTo(MONDAY));
        assertThat(provider.calls).isEqualTo(1);
    }

    private static Instant pragueTime(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(PRAGUE).toInstant();
    }

    private static ExchangeRates rates(LocalDate validFor) {
        return new ExchangeRates(validFor, Map.of("USD", new BigDecimal("21.788")));
    }

    private static class ScriptedProvider implements ExchangeRateProvider {

        private final Queue<Supplier<ExchangeRates>> results = new ArrayDeque<>();
        private Supplier<ExchangeRates> last;
        int calls;

        ScriptedProvider willReturn(ExchangeRates rates) {
            results.add(() -> rates);
            return this;
        }

        ScriptedProvider willFail() {
            results.add(() -> {
                throw new ExchangeRateProviderException("CNB is down");
            });
            return this;
        }

        @Override
        public ExchangeRates fetchLatest() {
            calls++;
            if (!results.isEmpty()) {
                last = results.poll();
            }
            return last.get();
        }
    }
}
