package com.example.wallet.service;

import com.example.wallet.domain.ExchangeRates;

@FunctionalInterface
public interface ExchangeRateProvider {

    ExchangeRates fetchLatest();
}
