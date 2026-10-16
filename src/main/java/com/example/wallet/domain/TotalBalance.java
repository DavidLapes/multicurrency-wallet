package com.example.wallet.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TotalBalance(String currency, BigDecimal amount, LocalDate ratesValidFor, boolean ratesStale) {
}
