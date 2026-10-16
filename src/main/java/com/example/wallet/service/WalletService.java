package com.example.wallet.service;

import com.example.wallet.domain.Balance;
import com.example.wallet.domain.CurrencyUnits;
import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.domain.exception.InvalidAmountException;
import com.example.wallet.domain.TotalBalance;
import com.example.wallet.domain.exception.UnsupportedCurrencyException;
import com.example.wallet.storage.Wallet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private final Wallet wallet;
    private final ExchangeRateService exchangeRateService;

    public WalletService(Wallet wallet, ExchangeRateService exchangeRateService) {
        this.wallet = wallet;
        this.exchangeRateService = exchangeRateService;
    }

    public Balance deposit(String currency, BigDecimal amount) {
        BigDecimal validAmount = validate(currency, amount);
        BigDecimal newBalance = wallet.deposit(currency, validAmount);
        log.atInfo()
                .addKeyValue("currency", currency)
                .addKeyValue("amount", validAmount)
                .log("Deposit accepted");
        return new Balance(currency, newBalance);
    }

    public Balance withdraw(String currency, BigDecimal amount) {
        BigDecimal validAmount = validate(currency, amount);
        BigDecimal newBalance = wallet.withdraw(currency, validAmount);
        log.atInfo()
                .addKeyValue("currency", currency)
                .addKeyValue("amount", validAmount)
                .log("Withdrawal accepted");
        return new Balance(currency, newBalance);
    }

    public List<Balance> balances() {
        return wallet.balances().entrySet().stream()
                .map(entry -> new Balance(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(Balance::currency))
                .toList();
    }

    public TotalBalance total(String targetCurrency) {
        RatesSnapshot snapshot = exchangeRateService.currentRates();
        ExchangeRates rates = snapshot.rates();
        requireSupported(targetCurrency, rates);

        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> balance : wallet.balances().entrySet()) {
            String currency = balance.getKey();
            if (!rates.isSupported(currency)) {
                throw new ExchangeRatesUnavailableException("No exchange rate for held currency " + currency);
            }
            sum = sum.add(rates.convert(balance.getValue(), currency, targetCurrency));
        }

        BigDecimal total = sum.setScale(CurrencyUnits.numberOfFractionDigits(targetCurrency), ROUNDING);
        return new TotalBalance(targetCurrency, total, rates.validFor(), snapshot.stale());
    }

    private BigDecimal validate(String currency, BigDecimal amount) {
        requireSupported(currency, exchangeRateService.currentRates().rates());
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        int fractionDigits = CurrencyUnits.numberOfFractionDigits(currency);
        if (amount.stripTrailingZeros().scale() > fractionDigits) {
            throw new InvalidAmountException("%s amounts can have at most %d decimal places, got %s"
                    .formatted(currency, fractionDigits, amount.toPlainString()));
        }
        return amount.setScale(fractionDigits, RoundingMode.UNNECESSARY);
    }

    private static void requireSupported(String currency, ExchangeRates rates) {
        if (!rates.isSupported(currency)) {
            throw new UnsupportedCurrencyException(currency, rates.supportedCurrencies());
        }
    }
}
