package com.example.wallet.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WithdrawalRequest(
        @Schema(description = "ISO 4217 code of the balance to withdraw from", example = "USD")
        @NotNull
        @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase ISO 4217 code, e.g. USD")
        String currency,

        @Schema(description = "Positive amount with at most the currency's decimal places (2 for USD, 0 for JPY); "
                + "must not exceed the balance of that currency",
                example = "3.00", type = "string")
        @NotNull
        @Positive
        @Digits(integer = 15, fraction = 10)
        BigDecimal amount) {
}
