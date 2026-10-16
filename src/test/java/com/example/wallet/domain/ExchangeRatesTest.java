package com.example.wallet.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExchangeRatesTest {

    private final ExchangeRates rates = new ExchangeRates(LocalDate.of(2026, 10, 13), Map.of(
            "USD", new BigDecimal("22.00"),
            "EUR", new BigDecimal("25.52")));

    @Test
    void convertsBetweenTwoForeignCurrenciesThroughCzk() {
        assertThat(rates.convert(new BigDecimal("5"), "EUR", "USD")).isEqualByComparingTo("5.8");
    }

    @Test
    void convertsToAndFromCzk() {
        assertThat(rates.convert(new BigDecimal("220"), "CZK", "USD")).isEqualByComparingTo("10");
        assertThat(rates.convert(new BigDecimal("10"), "USD", "CZK")).isEqualByComparingTo("220");
    }

    @Test
    void sameCurrencyIsReturnedUnchanged() {
        BigDecimal amount = new BigDecimal("7.00");

        assertThat(rates.convert(amount, "USD", "USD")).isSameAs(amount);
    }

    @Test
    void keepsFullPrecisionWithoutRounding() {
        assertThat(rates.convert(new BigDecimal("7"), "USD", "EUR").scale()).isGreaterThan(20);
    }

    @Test
    void czkIsAlwaysSupported() {
        assertThat(rates.isSupported("CZK")).isTrue();
        assertThat(rates.isSupported("USD")).isTrue();
        assertThat(rates.isSupported("GBP")).isFalse();
        assertThat(rates.supportedCurrencies()).containsExactly("CZK", "EUR", "USD");
    }

    @Test
    void convertingAnUnsupportedCurrencyFails() {
        assertThatThrownBy(() -> rates.convert(BigDecimal.ONE, "GBP", "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GBP");
    }

    @Test
    void isImmutable() {
        assertThatThrownBy(() -> rates.czkPerUnit().put("GBP", BigDecimal.TEN))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
