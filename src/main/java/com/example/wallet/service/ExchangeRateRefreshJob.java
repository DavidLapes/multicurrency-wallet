package com.example.wallet.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Loads rates in the background so requests rarely wait for CNB.
 * Requests still fetch rates themselves if this job has not succeeded yet.
 */
@Component
public class ExchangeRateRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateRefreshJob.class);

    private final ExchangeRateService exchangeRateService;
    private final boolean refreshOnStartup;

    public ExchangeRateRefreshJob(ExchangeRateService exchangeRateService, ExchangeRateRefreshProperties properties) {
        this.exchangeRateService = exchangeRateService;
        this.refreshOnStartup = properties.onStartup();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (refreshOnStartup) {
            refresh();
        }
    }

    @Scheduled(cron = "${exchange-rates.refresh.cron}", zone = "${exchange-rates.time-zone}")
    public void refresh() {
        try {
            RatesSnapshot snapshot = exchangeRateService.currentRates();
            log.atInfo()
                    .addKeyValue("validFor", snapshot.rates().validFor())
                    .addKeyValue("stale", snapshot.stale())
                    .log("Background exchange rate refresh done");
        } catch (ExchangeRatesUnavailableException e) {
            log.atWarn().setCause(e).log("Background exchange rate refresh failed, requests will retry");
        }
    }
}
