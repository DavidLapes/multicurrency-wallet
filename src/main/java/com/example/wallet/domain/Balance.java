package com.example.wallet.domain;

import java.math.BigDecimal;

public record Balance(String currency, BigDecimal amount) {
}
