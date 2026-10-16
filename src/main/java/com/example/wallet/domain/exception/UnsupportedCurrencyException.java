package com.example.wallet.domain.exception;

import java.util.Set;

public class UnsupportedCurrencyException extends RuntimeException {

    private final String currency;
    private final Set<String> supportedCurrencies;

    public UnsupportedCurrencyException(String currency, Set<String> supportedCurrencies) {
        super("Currency %s is not supported".formatted(currency));
        this.currency = currency;
        this.supportedCurrencies = Set.copyOf(supportedCurrencies);
    }

    public String currency() {
        return currency;
    }

    public Set<String> supportedCurrencies() {
        return supportedCurrencies;
    }
}
