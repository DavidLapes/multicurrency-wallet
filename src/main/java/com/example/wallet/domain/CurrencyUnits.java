package com.example.wallet.domain;

import java.util.Currency;

public final class CurrencyUnits {

    private static final int DEFAULT_FRACTION_DIGITS = 2;

    private CurrencyUnits() {
    }

    public static int numberOfFractionDigits(String currencyCode) {
        try {
            int digits = Currency.getInstance(currencyCode).getDefaultFractionDigits();
            return digits >= 0 ? digits : DEFAULT_FRACTION_DIGITS;
        } catch (IllegalArgumentException unknownCode) {
            return DEFAULT_FRACTION_DIGITS;
        }
    }
}
