package com.example.wallet.service;

public class ExchangeRatesUnavailableException extends RuntimeException {

    public ExchangeRatesUnavailableException(String message) {
        super(message);
    }

    public ExchangeRatesUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
