package com.example.wallet.domain;

import com.example.wallet.domain.exception.InsufficientFundsException;
import com.example.wallet.storage.Wallet;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    private final Wallet wallet = new Wallet();

    @Test
    void depositsAddUpPerCurrency() {
        wallet.deposit("USD", new BigDecimal("10.00"));
        BigDecimal balance = wallet.deposit("USD", new BigDecimal("2.50"));
        wallet.deposit("EUR", new BigDecimal("5.00"));

        assertThat(balance).isEqualTo(new BigDecimal("12.50"));
        assertThat(wallet.balances()).isEqualTo(Map.of(
                "USD", new BigDecimal("12.50"),
                "EUR", new BigDecimal("5.00")));
    }

    @Test
    void withdrawalReducesOnlyThatCurrency() {
        wallet.deposit("USD", new BigDecimal("10.00"));
        wallet.deposit("EUR", new BigDecimal("5.00"));

        BigDecimal balance = wallet.withdraw("USD", new BigDecimal("3.00"));

        assertThat(balance).isEqualTo(new BigDecimal("7.00"));
        assertThat(wallet.balances().get("EUR")).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void canWithdrawTheWholeBalance() {
        wallet.deposit("USD", new BigDecimal("10.00"));

        assertThat(wallet.withdraw("USD", new BigDecimal("10.00"))).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void cannotWithdrawMoreThanTheBalanceAndBalanceStaysUnchanged() {
        wallet.deposit("USD", new BigDecimal("10.00"));

        assertThatThrownBy(() -> wallet.withdraw("USD", new BigDecimal("10.01")))
                .isInstanceOfSatisfying(InsufficientFundsException.class, e -> {
                    assertThat(e.currency()).isEqualTo("USD");
                    assertThat(e.available()).isEqualTo(new BigDecimal("10.00"));
                    assertThat(e.requested()).isEqualTo(new BigDecimal("10.01"));
                });
        assertThat(wallet.balances().get("USD")).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void cannotWithdrawACurrencyNeverDeposited() {
        assertThatThrownBy(() -> wallet.withdraw("USD", new BigDecimal("1.00")))
                .isInstanceOfSatisfying(InsufficientFundsException.class,
                        e -> assertThat(e.available()).isEqualTo(new BigDecimal("0.00")));
        assertThat(wallet.balances()).isEmpty();
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> wallet.deposit("USD", BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.withdraw("USD", new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.deposit("USD", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void balancesAreAnImmutableCopy() {
        wallet.deposit("USD", new BigDecimal("10.00"));
        Map<String, BigDecimal> snapshot = wallet.balances();

        wallet.deposit("USD", new BigDecimal("5.00"));

        assertThat(snapshot.get("USD")).isEqualTo(new BigDecimal("10.00"));
        assertThatThrownBy(() -> snapshot.put("EUR", BigDecimal.ONE)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws InterruptedException {
        wallet.deposit("USD", new BigDecimal("100.00"));
        int attempts = 1_000;
        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService threads = Executors.newFixedThreadPool(32)) {
            for (int i = 0; i < attempts; i++) {
                threads.submit(() -> {
                    start.await();
                    try {
                        wallet.withdraw("USD", new BigDecimal("1.00"));
                        succeeded.incrementAndGet();
                    } catch (InsufficientFundsException expected) {
                    }
                    return null;
                });
            }
            start.countDown();
        }

        assertThat(succeeded.get()).isEqualTo(100);
        assertThat(wallet.balances().get("USD")).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void concurrentDepositsAreNotLost() throws InterruptedException {
        int deposits = 1_000;
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService threads = Executors.newFixedThreadPool(32)) {
            for (int i = 0; i < deposits; i++) {
                threads.submit(() -> {
                    start.await();
                    wallet.deposit("USD", new BigDecimal("1.00"));
                    return null;
                });
            }
            start.countDown();
        }

        assertThat(wallet.balances().get("USD")).isEqualTo(new BigDecimal("1000.00"));
    }
}
