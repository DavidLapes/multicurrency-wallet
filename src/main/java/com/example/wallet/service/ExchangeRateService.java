package com.example.wallet.service;

import com.example.wallet.domain.ExchangeRates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateProvider provider;
    private final Clock clock;
    private final ZoneId timeZone;
    private final LocalTime publicationTime;
    private final Duration retryInterval;
    private final Object refreshLock = new Object();

    private volatile CacheEntry cache;

    public ExchangeRateService(ExchangeRateProvider provider, Clock clock, ExchangeRateProperties properties) {
        this.provider = provider;
        this.clock = clock;
        this.timeZone = properties.timeZone();
        this.publicationTime = properties.publicationTime();
        this.retryInterval = properties.retryInterval();
    }

    public RatesSnapshot currentRates() {
        CacheEntry entry = cache;
        if (isFresh(entry)) {
            return entry.toSnapshot();
        }
        synchronized (refreshLock) {
            entry = cache;
            if (isFresh(entry)) {
                return entry.toSnapshot();
            }
            return refresh(entry);
        }
    }

    /** The cached rates, without ever calling the provider. Empty until the first successful load. */
    public Optional<RatesSnapshot> cachedRates() {
        return Optional.ofNullable(cache).map(CacheEntry::toSnapshot);
    }

    private boolean isFresh(CacheEntry entry) {
        if (entry == null) {
            return false;
        }
        Instant now = clock.instant();
        boolean hasLatestFixing = !entry.rates().validFor().isBefore(latestPublishedFixingDate(now));
        boolean attemptedRecently = now.isBefore(entry.lastAttemptAt().plus(retryInterval));
        return hasLatestFixing || attemptedRecently;
    }

    private LocalDate latestPublishedFixingDate(Instant now) {
        ZonedDateTime localNow = now.atZone(timeZone);
        LocalDate date = localNow.toLocalTime().isBefore(publicationTime)
                ? localNow.toLocalDate().minusDays(1)
                : localNow.toLocalDate();
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }
        return date;
    }

    private RatesSnapshot refresh(CacheEntry previous) {
        Instant now = clock.instant();
        try {
            ExchangeRates rates = provider.fetchLatest();
            log.atInfo()
                    .addKeyValue("validFor", rates.validFor())
                    .addKeyValue("currencies", rates.czkPerUnit().size())
                    .log("Loaded exchange rates");
            cache = new CacheEntry(rates, now, false);
        } catch (ExchangeRateProviderException e) {
            if (previous == null) {
                log.atError().setCause(e).log("Exchange rate provider failed and no rates are cached");
                throw new ExchangeRatesUnavailableException("Exchange rates are unavailable, try again later", e);
            }
            log.atWarn()
                    .setCause(e)
                    .addKeyValue("validFor", previous.rates().validFor())
                    .log("Exchange rate refresh failed, using cached rates");
            cache = new CacheEntry(previous.rates(), now, true);
        }
        return cache.toSnapshot();
    }

    private record CacheEntry(ExchangeRates rates, Instant lastAttemptAt, boolean lastAttemptFailed) {

        RatesSnapshot toSnapshot() {
            return new RatesSnapshot(rates, lastAttemptFailed, lastAttemptAt);
        }
    }
}
