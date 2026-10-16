package com.example.wallet.integration.cnb;

import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.service.ExchangeRateProviderException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
record CnbDailyRatesResponse(List<Rate> rates) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Rate(LocalDate validFor, String currencyCode, Integer amount, BigDecimal rate) {

        BigDecimal czkPerUnit() {
            return rate.divide(BigDecimal.valueOf(amount), ExchangeRates.CALCULATION_PRECISION);
        }

        boolean isValid() {
            return validFor != null
                    && currencyCode != null && !currencyCode.isBlank()
                    && amount != null && amount > 0
                    && rate != null && rate.signum() > 0;
        }
    }

    ExchangeRates toExchangeRates() {
        if (rates == null || rates.isEmpty()) {
            throw new ExchangeRateProviderException("CNB returned no rates");
        }
        LocalDate validFor = rates.getFirst().validFor();
        Map<String, BigDecimal> czkPerUnit = new HashMap<>();
        for (Rate rate : rates) {
            if (!rate.isValid()) {
                throw new ExchangeRateProviderException("CNB returned an invalid rate: " + rate);
            }
            if (!rate.validFor().equals(validFor)) {
                throw new ExchangeRateProviderException("CNB returned rates for more than one date");
            }
            czkPerUnit.put(rate.currencyCode(), rate.czkPerUnit());
        }
        return new ExchangeRates(validFor, czkPerUnit);
    }
}
