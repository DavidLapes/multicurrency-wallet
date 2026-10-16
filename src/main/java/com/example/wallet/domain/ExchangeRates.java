package com.example.wallet.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record ExchangeRates(LocalDate validFor, Map<String, BigDecimal> czkPerUnit) {

    public static final String CZK = "CZK";

    public static final MathContext CALCULATION_PRECISION = MathContext.DECIMAL128;

    public ExchangeRates {
        Objects.requireNonNull(validFor, "validFor");
        czkPerUnit = Map.copyOf(czkPerUnit);
    }

    public boolean isSupported(String currency) {
        return CZK.equals(currency) || czkPerUnit.containsKey(currency);
    }

    public Set<String> supportedCurrencies() {
        Set<String> currencies = new TreeSet<>(czkPerUnit.keySet());
        currencies.add(CZK);
        return Collections.unmodifiableSet(currencies);
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }
        BigDecimal amountInCzk = amount.multiply(czkPerUnitOf(fromCurrency), CALCULATION_PRECISION);
        return amountInCzk.divide(czkPerUnitOf(toCurrency), CALCULATION_PRECISION);
    }

    private BigDecimal czkPerUnitOf(String currency) {
        if (CZK.equals(currency)) {
            return BigDecimal.ONE;
        }
        BigDecimal rate = czkPerUnit.get(currency);
        if (rate == null) {
            throw new IllegalArgumentException("No exchange rate for " + currency);
        }
        return rate;
    }
}
