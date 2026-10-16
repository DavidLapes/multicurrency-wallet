package com.example.wallet.api.dto;

import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.service.RatesSnapshot;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;

public record RatesResponse(
        @Schema(description = "Date of the fixing", example = "2026-10-13")
        LocalDate validFor,
        @Schema(description = "True if the last refresh from CNB failed and older cached rates are shown")
        boolean stale,
        @Schema(description = "CZK per one unit of each currency, sorted by code")
        Map<String, BigDecimal> czkPerUnit) {

    public static RatesResponse from(RatesSnapshot snapshot) {
        ExchangeRates rates = snapshot.rates();
        return new RatesResponse(rates.validFor(), snapshot.stale(), new TreeMap<>(rates.czkPerUnit()));
    }
}
