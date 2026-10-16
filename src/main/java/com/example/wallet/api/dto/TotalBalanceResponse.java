package com.example.wallet.api.dto;

import com.example.wallet.domain.TotalBalance;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TotalBalanceResponse(
        @Schema(example = "USD")
        String currency,
        @Schema(description = "Decimal string, rounded half-even to the currency's decimal places", example = "12.80", type = "string")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal amount,
        @Schema(description = "Date of the CNB fixing used. Earlier than today on weekends, holidays and before 14:30 Prague time",
                example = "2026-10-13")
        LocalDate ratesValidFor,
        @Schema(description = "True if CNB could not be reached and older cached rates were used")
        boolean ratesStale) {

    public static TotalBalanceResponse from(TotalBalance total) {
        return new TotalBalanceResponse(total.currency(), total.amount(), total.ratesValidFor(), total.ratesStale());
    }
}
