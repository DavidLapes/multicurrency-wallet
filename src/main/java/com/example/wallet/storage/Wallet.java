package com.example.wallet.storage;

import com.example.wallet.domain.exception.InsufficientFundsException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class Wallet {

    private final ConcurrentMap<String, BigDecimal> balances = new ConcurrentHashMap<>();

    public BigDecimal deposit(String currency, BigDecimal amount) {
        requirePositive(amount);
        return balances.merge(currency, amount, BigDecimal::add);
    }

    public BigDecimal withdraw(String currency, BigDecimal amount) {
        requirePositive(amount);
        return balances.compute(currency, (code, balance) -> {
            BigDecimal available = balance == null ? BigDecimal.ZERO.setScale(amount.scale()) : balance;
            if (available.compareTo(amount) < 0) {
                throw new InsufficientFundsException(code, available, amount);
            }
            return available.subtract(amount);
        });
    }

    public Map<String, BigDecimal> balances() {
        return Map.copyOf(balances);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got " + amount);
        }
    }
}
