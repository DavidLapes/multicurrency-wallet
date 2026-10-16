package com.example.wallet.domain.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    private final String currency;
    private final BigDecimal available;
    private final BigDecimal requested;

    public InsufficientFundsException(String currency, BigDecimal available, BigDecimal requested) {
        super("Cannot withdraw %s %s, the balance is %s %s"
                .formatted(requested.toPlainString(), currency, available.toPlainString(), currency));
        this.currency = currency;
        this.available = available;
        this.requested = requested;
    }

    public String currency() {
        return currency;
    }

    public BigDecimal available() {
        return available;
    }

    public BigDecimal requested() {
        return requested;
    }
}
