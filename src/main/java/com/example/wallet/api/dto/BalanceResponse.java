package com.example.wallet.api.dto;

import com.example.wallet.domain.Balance;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record BalanceResponse(
        @Schema(example = "USD", type = "string")
        String currency,
        @Schema(example = "7.00", type = "string")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal amount) {

    public static BalanceResponse from(Balance balance) {
        return new BalanceResponse(balance.currency(), balance.amount());
    }
}
