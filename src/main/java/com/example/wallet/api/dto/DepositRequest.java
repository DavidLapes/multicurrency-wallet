package com.example.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequest(
        @Schema(description = "ISO 4217 code: CZK or a currency quoted by CNB", example = "USD")
        @NotNull
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase ISO 4217 code, e.g. USD")
        String currency,

        @Schema(description = "Positive amount with at most the currency's decimal places (2 for USD, 0 for JPY)",
                example = "10.50", type = "string")
        @NotNull
        @Positive
        @Digits(integer = 15, fraction = 10)
        BigDecimal amount) {
}
