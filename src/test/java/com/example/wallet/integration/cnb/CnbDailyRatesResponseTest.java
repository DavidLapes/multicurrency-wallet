package com.example.wallet.integration.cnb;

import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.integration.cnb.CnbDailyRatesResponse.Rate;
import com.example.wallet.service.ExchangeRateProviderException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Mapping from the CNB response to our model, without any HTTP. */
class CnbDailyRatesResponseTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 13);

    @Test
    void dividesMultiUnitQuotesDownToOneUnit() {
        CnbDailyRatesResponse response = new CnbDailyRatesResponse(List.of(
                new Rate(DATE, "USD", 1, new BigDecimal("21.788")),
                new Rate(DATE, "HUF", 100, new BigDecimal("6.690")),
                new Rate(DATE, "IDR", 1000, new BigDecimal("1.218"))));

        ExchangeRates rates = response.toExchangeRates();

        assertThat(rates.validFor()).isEqualTo(DATE);
        assertThat(rates.czkPerUnit().get("USD")).isEqualByComparingTo("21.788");
        assertThat(rates.czkPerUnit().get("HUF")).isEqualByComparingTo("0.0669");
        assertThat(rates.czkPerUnit().get("IDR")).isEqualByComparingTo("0.001218");
    }

    @Test
    void rejectsAnEmptyResponse() {
        assertThatThrownBy(() -> new CnbDailyRatesResponse(List.of()).toExchangeRates())
                .isInstanceOf(ExchangeRateProviderException.class);
        assertThatThrownBy(() -> new CnbDailyRatesResponse(null).toExchangeRates())
                .isInstanceOf(ExchangeRateProviderException.class);
    }

    @Test
    void rejectsInvalidRates() {
        assertInvalid(new Rate(DATE, "USD", 0, new BigDecimal("21.788")));
        assertInvalid(new Rate(DATE, "USD", 1, new BigDecimal("-1")));
        assertInvalid(new Rate(DATE, "USD", 1, null));
        assertInvalid(new Rate(DATE, null, 1, new BigDecimal("21.788")));
        assertInvalid(new Rate(null, "USD", 1, new BigDecimal("21.788")));
    }

    @Test
    void rejectsRatesForMixedDates() {
        CnbDailyRatesResponse response = new CnbDailyRatesResponse(List.of(
                new Rate(DATE, "USD", 1, new BigDecimal("21.788")),
                new Rate(DATE.minusDays(1), "EUR", 1, new BigDecimal("24.375"))));

        assertThatThrownBy(response::toExchangeRates).isInstanceOf(ExchangeRateProviderException.class);
    }

    private static void assertInvalid(Rate rate) {
        assertThatThrownBy(() -> new CnbDailyRatesResponse(List.of(rate)).toExchangeRates())
                .isInstanceOf(ExchangeRateProviderException.class);
    }
}
