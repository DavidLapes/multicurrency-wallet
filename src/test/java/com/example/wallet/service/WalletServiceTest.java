package com.example.wallet.service;

import com.example.wallet.domain.Balance;
import com.example.wallet.domain.ExchangeRates;
import com.example.wallet.domain.exception.InsufficientFundsException;
import com.example.wallet.domain.exception.InvalidAmountException;
import com.example.wallet.domain.TotalBalance;
import com.example.wallet.domain.exception.UnsupportedCurrencyException;
import com.example.wallet.storage.Wallet;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceTest {

    private static final LocalDate FIXING_DATE = LocalDate.of(2026, 10, 13);

    private static final ExchangeRates RATES = new ExchangeRates(FIXING_DATE, Map.of(
            "USD", new BigDecimal("22.00"),
            "EUR", new BigDecimal("25.52"),
            "JPY", new BigDecimal("0.13779")));

    private final WalletService walletService = new WalletService(new Wallet(), new ExchangeRateService(
            () -> RATES,
            Clock.fixed(Instant.parse("2026-10-13T15:00:00Z"), ZoneOffset.UTC),
            new ExchangeRateProperties(ZoneId.of("Europe/Prague"), LocalTime.of(14, 30), Duration.ofMinutes(10))));

    @Test
    void assignmentExampleWorkflow() {
        walletService.deposit("USD", new BigDecimal("10"));
        walletService.deposit("EUR", new BigDecimal("5"));
        walletService.withdraw("USD", new BigDecimal("3"));

        assertThat(walletService.total("USD").amount()).isEqualTo(new BigDecimal("12.80"));
        assertThat(walletService.total("EUR").amount()).isEqualTo(new BigDecimal("11.03"));
    }

    @Test
    void returnsTheNewBalanceWithTheCurrencysDecimalPlaces() {
        assertThat(walletService.deposit("USD", new BigDecimal("10"))).isEqualTo(new Balance("USD", new BigDecimal("10.00")));
        assertThat(walletService.deposit("JPY", new BigDecimal("500"))).isEqualTo(new Balance("JPY", new BigDecimal("500")));
        assertThat(walletService.withdraw("USD", new BigDecimal("2.5"))).isEqualTo(new Balance("USD", new BigDecimal("7.50")));
    }

    @Test
    void acceptsTrailingZerosBeyondTheCurrencysDecimalPlaces() {
        assertThat(walletService.deposit("USD", new BigDecimal("1.500")).amount()).isEqualTo(new BigDecimal("1.50"));
    }

    @Test
    void listsBalancesSortedByCurrency() {
        walletService.deposit("USD", new BigDecimal("1"));
        walletService.deposit("CZK", new BigDecimal("2"));
        walletService.deposit("EUR", new BigDecimal("3"));

        assertThat(walletService.balances()).extracting(Balance::currency).containsExactly("CZK", "EUR", "USD");
    }

    @Test
    void czkIsAnOrdinaryCurrencyWithRateOne() {
        walletService.deposit("CZK", new BigDecimal("220"));

        assertThat(walletService.total("USD").amount()).isEqualTo(new BigDecimal("10.00"));
        assertThat(walletService.total("CZK").amount()).isEqualTo(new BigDecimal("220.00"));
    }

    @Test
    void totalIsRoundedToTheTargetCurrencysDecimalPlaces() {
        walletService.deposit("USD", new BigDecimal("1"));

        assertThat(walletService.total("JPY").amount()).isEqualTo(new BigDecimal("160"));
    }

    @Test
    void totalRoundsHalfToEven() {
        walletService.deposit("CZK", new BigDecimal("2.75"));

        assertThat(walletService.total("USD").amount()).isEqualTo(new BigDecimal("0.12"));
    }

    @Test
    void totalOfAnEmptyWalletIsZero() {
        assertThat(walletService.total("EUR").amount()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void totalReportsWhichRatesWereUsed() {
        TotalBalance total = walletService.total("USD");

        assertThat(total.ratesValidFor()).isEqualTo(FIXING_DATE);
        assertThat(total.ratesStale()).isFalse();
    }

    @Test
    void rejectsCurrenciesThatCannotBeConverted() {
        assertRejected(() -> walletService.deposit("GBP", BigDecimal.ONE), UnsupportedCurrencyException.class);
        assertRejected(() -> walletService.withdraw("GBP", BigDecimal.ONE), UnsupportedCurrencyException.class);
        assertRejected(() -> walletService.total("GBP"), UnsupportedCurrencyException.class);
    }

    @Test
    void unsupportedCurrencyErrorListsTheSupportedOnes() {
        assertThatThrownBy(() -> walletService.deposit("GBP", BigDecimal.ONE))
                .isInstanceOfSatisfying(UnsupportedCurrencyException.class,
                        e -> assertThat(e.supportedCurrencies()).containsExactlyInAnyOrder("CZK", "EUR", "JPY", "USD"));
    }

    @Test
    void rejectsAmountsWithMoreDecimalPlacesThanTheCurrencyHas() {
        assertRejected(() -> walletService.deposit("USD", new BigDecimal("1.001")), InvalidAmountException.class);
        assertRejected(() -> walletService.deposit("JPY", new BigDecimal("1.5")), InvalidAmountException.class);
        assertThat(walletService.balances()).isEmpty();
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertRejected(() -> walletService.deposit("USD", BigDecimal.ZERO), InvalidAmountException.class);
        assertRejected(() -> walletService.withdraw("USD", new BigDecimal("-5")), InvalidAmountException.class);
    }

    @Test
    void cannotWithdrawMoreThanTheBalanceOfThatCurrency() {
        walletService.deposit("USD", new BigDecimal("5"));
        walletService.deposit("EUR", new BigDecimal("100"));

        assertRejected(() -> walletService.withdraw("USD", new BigDecimal("5.01")), InsufficientFundsException.class);
    }

    private static void assertRejected(ThrowingCallable call, Class<? extends RuntimeException> expected) {
        assertThatThrownBy(call).isInstanceOf(expected);
    }
}
