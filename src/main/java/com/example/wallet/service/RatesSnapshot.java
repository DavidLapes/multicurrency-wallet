package com.example.wallet.service;

import com.example.wallet.domain.ExchangeRates;

import java.time.Instant;

/**
 * @param stale         true if the last attempt to reach the provider failed and older cached rates are used
 * @param lastCheckedAt when the provider was last contacted, successfully or not
 */
public record RatesSnapshot(ExchangeRates rates, boolean stale, Instant lastCheckedAt) {
}
